package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.exception.LeaveManagementException;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles policy version changes and retroactive corrections.
 *
 * When a policy is updated (e.g. entitlement changed from 25 -> 20 days, or
 * a misconfigured parental leave of 60 days corrected to 90), this service:
 *
 * 1. Creates a new version of the policy
 * 2. Deactivates the old version
 * 3. For all enrolled employees, computes the delta between old and new entitlement
 * 4. Adjusts pool balances accordingly (credit or debit)
 * 5. Records ledger entries for full auditability
 * 6. Updates enrollment to point to the new policy version
 *
 * This correctly handles employees who have already taken leave under the old policy.
 */
public class PolicyChangeService {

    private final LeavePolicyDAO policyDAO;
    private final LeavePoolDAO leavePoolDAO;
    private final LeaveLedgerDAO ledgerDAO;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;

    public PolicyChangeService(LeavePolicyDAO policyDAO,
                                LeavePoolDAO leavePoolDAO,
                                LeaveLedgerDAO ledgerDAO,
                                EmployeePolicyEnrollmentDAO enrollmentDAO) {
        this.policyDAO = policyDAO;
        this.leavePoolDAO = leavePoolDAO;
        this.ledgerDAO = ledgerDAO;
        this.enrollmentDAO = enrollmentDAO;
    }

    /**
     * Create a new version of a policy and apply the changes retroactively
     * to all enrolled employees.
     *
     * @param policyGroupId  the policy group to update
     * @param updatedPolicy  the new policy values (policyGroupId and version will be set automatically)
     * @param retroactive    if true, recompute balances for all enrolled employees
     * @param triggeredBy    who initiated the change
     * @return the newly created policy version
     */
    public LeavePolicy updatePolicy(String policyGroupId, LeavePolicy updatedPolicy,
                                     boolean retroactive, String triggeredBy) {
        // Get the current active version
        LeavePolicy currentActive = policyDAO.findActiveByGroupId(policyGroupId)
                .orElseThrow(() -> LeaveManagementException.notFound(
                        "No active policy found for group: " + policyGroupId));

        // Deactivate old version
        currentActive.setActive(false);
        policyDAO.save(currentActive);

        // Create new version
        int newVersion = currentActive.getVersion() + 1;
        updatedPolicy.setPolicyId(null); // ensure new row
        updatedPolicy.setPolicyGroupId(policyGroupId);
        updatedPolicy.setVersion(newVersion);
        updatedPolicy.setActive(true);
        LeavePolicy newPolicy = policyDAO.save(updatedPolicy);

        if (retroactive) {
            applyRetroactiveCorrection(currentActive, newPolicy, triggeredBy);
        }

        // Update all enrollments to point to new version
        List<EmployeePolicyEnrollment> enrollments = enrollmentDAO.findByPolicyGroupId(policyGroupId);
        for (EmployeePolicyEnrollment enrollment : enrollments) {
            enrollment.setPolicy(newPolicy);
            enrollmentDAO.save(enrollment);
        }

        return newPolicy;
    }

    /**
     * Apply retroactive balance corrections for all employees enrolled in the policy group.
     */
    private void applyRetroactiveCorrection(LeavePolicy oldPolicy, LeavePolicy newPolicy,
                                             String triggeredBy) {
        List<EmployeePolicyEnrollment> enrollments =
                enrollmentDAO.findByPolicyGroupId(oldPolicy.getPolicyGroupId());

        BigDecimal entitlementDelta = newPolicy.getEntitlementDays()
                .subtract(oldPolicy.getEntitlementDays());

        for (EmployeePolicyEnrollment enrollment : enrollments) {
            Employee employee = enrollment.getEmployee();

            // Find the current cycle pool(s) for this employee
            List<LeavePool> pools = leavePoolDAO.findByEmployeeAndLeaveType(
                    employee.getEmployeeId(), oldPolicy.getLeaveType());

            for (LeavePool pool : pools) {
                if (pool.getPoolType() != PoolType.CURRENT_CYCLE) continue;

                // Calculate the pro-rated delta based on accrual method
                BigDecimal adjustmentDelta = calculateAdjustmentDelta(
                        employee, oldPolicy, newPolicy, pool, entitlementDelta);

                if (adjustmentDelta.compareTo(BigDecimal.ZERO) == 0) continue;

                // Apply the adjustment
                pool.setCredited(pool.getCredited().add(adjustmentDelta));

                // Ensure credited doesn't go below used + held
                BigDecimal minCredited = pool.getUsed().add(pool.getHeld());
                if (pool.getCredited().compareTo(minCredited) < 0) {
                    pool.setCredited(minCredited);
                    adjustmentDelta = minCredited.subtract(
                            pool.getCredited().subtract(adjustmentDelta));
                }

                pool.setPolicy(newPolicy);
                leavePoolDAO.save(pool);

                // Record in ledger
                LeaveLedgerEntry entry = LeaveLedgerEntry.builder()
                        .employee(employee)
                        .leaveType(newPolicy.getLeaveType())
                        .poolType(PoolType.CURRENT_CYCLE)
                        .eventType(LedgerEventType.POLICY_ADJUSTMENT)
                        .delta(adjustmentDelta)
                        .balanceAfter(pool.getAvailable())
                        .cycleStart(pool.getCycleStart())
                        .policy(newPolicy)
                        .triggeredBy(triggeredBy)
                        .note("Retroactive policy correction: v" + oldPolicy.getVersion() +
                              " -> v" + newPolicy.getVersion() +
                              ". Entitlement: " + oldPolicy.getEntitlementDays() +
                              " -> " + newPolicy.getEntitlementDays())
                        .build();
                ledgerDAO.save(entry);
            }
        }
    }

    /**
     * Calculate the adjustment delta based on how much of the old entitlement
     * was already accrued vs the new entitlement.
     */
    private BigDecimal calculateAdjustmentDelta(Employee employee, LeavePolicy oldPolicy,
                                                 LeavePolicy newPolicy, LeavePool pool,
                                                 BigDecimal fullDelta) {
        return switch (newPolicy.getAccrualMethod()) {
            case MONTHLY -> {
                // For monthly accrual, adjust only the already-accrued portion proportionally
                // If old policy had 24 days (2/month) and we're 6 months in, credited = 12
                // New policy has 30 days (2.5/month), so correct credited = 15, delta = +3
                int monthsElapsed = LeaveCycleCalculator.monthsIntoCycle(
                        employee, newPolicy, LocalDate.now());
                BigDecimal oldMonthly = oldPolicy.getEntitlementDays()
                        .divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);
                BigDecimal newMonthly = newPolicy.getEntitlementDays()
                        .divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);
                BigDecimal expectedOld = oldMonthly.multiply(BigDecimal.valueOf(monthsElapsed));
                BigDecimal expectedNew = newMonthly.multiply(BigDecimal.valueOf(monthsElapsed));
                yield expectedNew.subtract(expectedOld)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }
            case YEARLY, ONE_TIME -> fullDelta; // Full delta applies immediately
        };
    }

    /**
     * Get the full version history for a policy group.
     */
    public List<LeavePolicy> getPolicyHistory(String policyGroupId) {
        return policyDAO.findAllVersionsByGroupId(policyGroupId);
    }
}
