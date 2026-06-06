package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.exception.LeaveManagementException;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles accrual and granting of leave entitlements into employee pools.
 *
 * - MONTHLY accrual:  entitlementDays / 12 credited each month
 * - YEARLY grant:     full entitlement credited at cycle start
 * - ONE_TIME grant:   single grant when eligibility is met (e.g. parental leave)
 */
public class LeaveAccrualService {

    private final LeavePoolDAO leavePoolDAO;
    private final LeaveLedgerDAO ledgerDAO;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;

    public LeaveAccrualService(LeavePoolDAO leavePoolDAO,
                               LeaveLedgerDAO ledgerDAO,
                               EmployeePolicyEnrollmentDAO enrollmentDAO) {
        this.leavePoolDAO = leavePoolDAO;
        this.ledgerDAO = ledgerDAO;
        this.enrollmentDAO = enrollmentDAO;
    }

    /**
     * Process accrual for a single employee under a specific policy.
     * Called by a scheduler or on-demand.
     */
    public void processAccrual(Employee employee, LeavePolicy policy, LocalDate asOfDate) {
        // Check tenure eligibility
        if (LeaveCycleCalculator.tenureYears(employee, asOfDate) < policy.getMinTenureYears()) {
            return; // Not yet eligible
        }

        LocalDate cycleStart = LeaveCycleCalculator.currentCycleStart(employee, policy, asOfDate);
        LocalDate cycleEnd = LeaveCycleCalculator.currentCycleEnd(employee, policy, asOfDate);

        // Get or create the current cycle pool
        LeavePool pool = leavePoolDAO
                .findByEmployeeLeaveTypePoolCycle(
                        employee.getEmployeeId(), policy.getLeaveType(),
                        PoolType.CURRENT_CYCLE, cycleStart)
                .orElseGet(() -> createPool(employee, policy, PoolType.CURRENT_CYCLE,
                                           cycleStart, cycleEnd, null));

        switch (policy.getAccrualMethod()) {
            case MONTHLY -> processMonthlyAccrual(employee, policy, pool, asOfDate, cycleStart);
            case YEARLY -> processYearlyGrant(employee, policy, pool, cycleStart);
            case ONE_TIME -> processOneTimeGrant(employee, policy, pool, cycleStart);
        }
    }

    private void processMonthlyAccrual(Employee employee, LeavePolicy policy,
                                        LeavePool pool, LocalDate asOfDate, LocalDate cycleStart) {
        // Calculate expected total accrual up to this month
        int monthsElapsed = LeaveCycleCalculator.monthsIntoCycle(employee, policy, asOfDate);
        BigDecimal monthlyRate = policy.getEntitlementDays()
                .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = monthlyRate
                .multiply(BigDecimal.valueOf(monthsElapsed))
                .setScale(2, RoundingMode.HALF_UP);

        // Credit the difference (idempotent — safe to call multiple times in same month)
        BigDecimal delta = expectedTotal.subtract(pool.getCredited());
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            pool.setCredited(expectedTotal);
            leavePoolDAO.save(pool);

            recordLedger(employee, policy, pool, LedgerEventType.ACCRUAL,
                         delta, pool.getAvailable(), cycleStart, null, "SCHEDULER",
                         "Monthly accrual: month " + monthsElapsed + " of cycle");
        }
    }

    private void processYearlyGrant(Employee employee, LeavePolicy policy,
                                     LeavePool pool, LocalDate cycleStart) {
        // Full entitlement granted once at cycle start (idempotent)
        if (pool.getCredited().compareTo(policy.getEntitlementDays()) < 0) {
            BigDecimal delta = policy.getEntitlementDays().subtract(pool.getCredited());
            pool.setCredited(policy.getEntitlementDays());
            leavePoolDAO.save(pool);

            recordLedger(employee, policy, pool, LedgerEventType.GRANT,
                         delta, pool.getAvailable(), cycleStart, null, "SCHEDULER",
                         "Yearly grant for cycle starting " + cycleStart);
        }
    }

    private void processOneTimeGrant(Employee employee, LeavePolicy policy,
                                      LeavePool pool, LocalDate cycleStart) {
        // Grant once if not already granted
        if (pool.getCredited().compareTo(BigDecimal.ZERO) == 0) {
            pool.setCredited(policy.getEntitlementDays());
            leavePoolDAO.save(pool);

            recordLedger(employee, policy, pool, LedgerEventType.GRANT,
                         policy.getEntitlementDays(), pool.getAvailable(), cycleStart,
                         null, "SYSTEM", "One-time grant: " + policy.getLeaveType());
        }
    }

    /**
     * Process accrual for ALL employees enrolled in a policy group.
     * Typically called by a scheduled job.
     */
    public void processAccrualForPolicyGroup(String policyGroupId, LocalDate asOfDate) {
        List<EmployeePolicyEnrollment> enrollments =
                enrollmentDAO.findByPolicyGroupId(policyGroupId);
        for (EmployeePolicyEnrollment enrollment : enrollments) {
            processAccrual(enrollment.getEmployee(), enrollment.getPolicy(), asOfDate);
        }
    }

    // -- helpers --

    private LeavePool createPool(Employee employee, LeavePolicy policy,
                                  PoolType poolType, LocalDate cycleStart,
                                  LocalDate cycleEnd, LocalDate expiryDate) {
        LeavePool pool = LeavePool.builder()
                .employee(employee)
                .policy(policy)
                .leaveType(policy.getLeaveType())
                .poolType(poolType)
                .cycleStart(cycleStart)
                .cycleEnd(cycleEnd)
                .credited(BigDecimal.ZERO)
                .used(BigDecimal.ZERO)
                .held(BigDecimal.ZERO)
                .expiryDate(expiryDate)
                .build();
        return leavePoolDAO.save(pool);
    }

    private void recordLedger(Employee employee, LeavePolicy policy, LeavePool pool,
                               LedgerEventType eventType, BigDecimal delta,
                               BigDecimal balanceAfter, LocalDate cycleStart,
                               LeaveRequest leaveRequest, String triggeredBy, String note) {
        LeaveLedgerEntry entry = LeaveLedgerEntry.builder()
                .employee(employee)
                .leaveType(policy.getLeaveType())
                .poolType(pool.getPoolType())
                .eventType(eventType)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .cycleStart(cycleStart)
                .leaveRequest(leaveRequest)
                .policy(policy)
                .triggeredBy(triggeredBy)
                .note(note)
                .build();
        ledgerDAO.save(entry);
    }
}
