package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles carry-forward logic at cycle boundaries.
 *
 * When a leave cycle ends:
 * 1. Calculate unused days in the current cycle pool
 * 2. Apply carry-forward rules from the policy (enabled? max days? expiry?)
 * 3. Create a CARRY_FORWARD pool in the new cycle with the rolled-over days
 * 4. Record ledger entries for full auditability
 *
 * If carry-forward is disabled, unused days simply expire (no new pool created).
 */
public class CarryForwardService {

    private final LeavePoolDAO leavePoolDAO;
    private final LeaveLedgerDAO ledgerDAO;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;

    public CarryForwardService(LeavePoolDAO leavePoolDAO,
                                LeaveLedgerDAO ledgerDAO,
                                EmployeePolicyEnrollmentDAO enrollmentDAO) {
        this.leavePoolDAO = leavePoolDAO;
        this.ledgerDAO = ledgerDAO;
        this.enrollmentDAO = enrollmentDAO;
    }

    /**
     * Process carry-forward for a single employee and policy at cycle boundary.
     *
     * @param employee   the employee
     * @param policy     the active policy
     * @param oldCycleStart start of the cycle that just ended
     */
    public void processCycleRollover(Employee employee, LeavePolicy policy, LocalDate oldCycleStart) {
        LocalDate newCycleStart = oldCycleStart.plusYears(1);
        LocalDate newCycleEnd = newCycleStart.plusYears(1).minusDays(1);

        // Find the old current-cycle pool
        LeavePool oldPool = leavePoolDAO
                .findByEmployeeLeaveTypePoolCycle(
                        employee.getEmployeeId(), policy.getLeaveType(),
                        PoolType.CURRENT_CYCLE, oldCycleStart)
                .orElse(null);

        if (oldPool == null) return;

        BigDecimal unused = oldPool.getAvailable();

        if (policy.isCarryForwardEnabled() && unused.compareTo(BigDecimal.ZERO) > 0) {
            // Cap at max carry-forward days
            BigDecimal carryAmount = unused;
            if (policy.getCarryForwardMaxDays() != null) {
                carryAmount = carryAmount.min(policy.getCarryForwardMaxDays());
            }

            // Determine expiry
            LocalDate expiryDate = null;
            if (policy.getCarryForwardExpiryMonths() != null) {
                expiryDate = newCycleStart.plusMonths(policy.getCarryForwardExpiryMonths());
            }

            // Create carry-forward pool in the new cycle
            LeavePool cfPool = LeavePool.builder()
                    .employee(employee)
                    .policy(policy)
                    .leaveType(policy.getLeaveType())
                    .poolType(PoolType.CARRY_FORWARD)
                    .cycleStart(newCycleStart)
                    .cycleEnd(newCycleEnd)
                    .credited(carryAmount)
                    .used(BigDecimal.ZERO)
                    .held(BigDecimal.ZERO)
                    .expiryDate(expiryDate)
                    .build();
            leavePoolDAO.save(cfPool);

            // Ledger: carry-forward in
            recordLedger(employee, policy, cfPool, LedgerEventType.CARRY_FORWARD_IN,
                         carryAmount, cfPool.getAvailable(), newCycleStart,
                         "SCHEDULER", "Carry-forward from cycle " + oldCycleStart +
                         ". Unused: " + unused + ", Carried: " + carryAmount);
        }

        // Any remaining unused days beyond carry-forward cap are lost (no ledger needed,
        // they simply don't appear in the new cycle)
    }

    /**
     * Expire carry-forward pools that have passed their expiry date.
     * Should be run daily by a scheduler.
     */
    public void expireCarryForwardPools(LocalDate asOfDate) {
        List<LeavePool> expired = leavePoolDAO.findExpiredCarryForwardPools(asOfDate);

        for (LeavePool pool : expired) {
            BigDecimal remaining = pool.getAvailable();
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // Zero out the remaining balance by marking it as used (expired)
                pool.setUsed(pool.getUsed().add(remaining));
                leavePoolDAO.save(pool);

                LeaveLedgerEntry entry = LeaveLedgerEntry.builder()
                        .employee(pool.getEmployee())
                        .leaveType(pool.getLeaveType())
                        .poolType(PoolType.CARRY_FORWARD)
                        .eventType(LedgerEventType.CARRY_FORWARD_EXPIRY)
                        .delta(remaining.negate())
                        .balanceAfter(BigDecimal.ZERO)
                        .cycleStart(pool.getCycleStart())
                        .policy(pool.getPolicy())
                        .triggeredBy("SCHEDULER")
                        .note("Carry-forward pool expired on " + pool.getExpiryDate())
                        .build();
                ledgerDAO.save(entry);
            }
        }
    }

    /**
     * Process carry-forward for all employees enrolled in a policy group.
     */
    public void processCycleRolloverForPolicyGroup(String policyGroupId, LocalDate oldCycleStart) {
        List<EmployeePolicyEnrollment> enrollments =
                enrollmentDAO.findByPolicyGroupId(policyGroupId);
        for (EmployeePolicyEnrollment enrollment : enrollments) {
            processCycleRollover(enrollment.getEmployee(), enrollment.getPolicy(), oldCycleStart);
        }
    }

    private void recordLedger(Employee employee, LeavePolicy policy, LeavePool pool,
                               LedgerEventType eventType, BigDecimal delta,
                               BigDecimal balanceAfter, LocalDate cycleStart,
                               String triggeredBy, String note) {
        LeaveLedgerEntry entry = LeaveLedgerEntry.builder()
                .employee(employee)
                .leaveType(policy.getLeaveType())
                .poolType(pool.getPoolType())
                .eventType(eventType)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .cycleStart(cycleStart)
                .policy(policy)
                .triggeredBy(triggeredBy)
                .note(note)
                .build();
        ledgerDAO.save(entry);
    }
}
