package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.exception.LeaveManagementException;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Manages leave balance operations:
 * - Querying available balance (across pools)
 * - Placing holds (reserve days when a request is submitted)
 * - Releasing holds (reject / cancel)
 * - Deducting days (on approval) — draws from carry-forward first, then current cycle
 * - Crediting back days (cancellation of approved leave)
 */
public class LeaveBalanceService {

    private final LeavePoolDAO leavePoolDAO;
    private final LeaveLedgerDAO ledgerDAO;

    public LeaveBalanceService(LeavePoolDAO leavePoolDAO, LeaveLedgerDAO ledgerDAO) {
        this.leavePoolDAO = leavePoolDAO;
        this.ledgerDAO = ledgerDAO;
    }

    /**
     * Total available balance for an employee and leave type across all pools.
     */
    public BigDecimal getAvailableBalance(Long employeeId, LeaveType leaveType) {
        List<LeavePool> pools = leavePoolDAO.findByEmployeeAndLeaveType(employeeId, leaveType);
        return pools.stream()
                .map(LeavePool::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Place a hold on days when a leave request is submitted.
     * Draws from carry-forward pool first (FIFO / earliest expiry first),
     * then from current cycle pool.
     */
    public void placeHold(Employee employee, LeaveType leaveType,
                          BigDecimal days, LeaveRequest request, LeavePolicy policy) {
        List<LeavePool> pools = getOrderedPools(employee.getEmployeeId(), leaveType);

        BigDecimal totalAvailable = pools.stream()
                .map(LeavePool::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAvailable.compareTo(days) < 0) {
            throw LeaveManagementException.badRequest(
                    "Insufficient balance. Available: " + totalAvailable + ", Requested: " + days);
        }

        BigDecimal remaining = days;
        for (LeavePool pool : pools) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal available = pool.getAvailable();
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal holdFromPool = remaining.min(available);
            pool.setHeld(pool.getHeld().add(holdFromPool));
            leavePoolDAO.save(pool);

            recordLedger(employee, leaveType, pool, LedgerEventType.HOLD,
                         holdFromPool.negate(), pool.getAvailable(),
                         pool.getCycleStart(), request, policy, "SYSTEM",
                         "Hold placed for leave request #" + request.getRequestId());

            remaining = remaining.subtract(holdFromPool);
        }
    }

    /**
     * Release held days back to the pools (on reject or cancel of pending request).
     */
    public void releaseHold(Employee employee, LeaveType leaveType,
                            BigDecimal days, LeaveRequest request, LeavePolicy policy) {
        List<LeavePool> pools = getOrderedPools(employee.getEmployeeId(), leaveType);

        BigDecimal remaining = days;
        // Release in reverse order (current cycle first, then carry-forward)
        for (int i = pools.size() - 1; i >= 0 && remaining.compareTo(BigDecimal.ZERO) > 0; i--) {
            LeavePool pool = pools.get(i);
            BigDecimal heldInPool = pool.getHeld();
            if (heldInPool.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal releaseFromPool = remaining.min(heldInPool);
            pool.setHeld(pool.getHeld().subtract(releaseFromPool));
            leavePoolDAO.save(pool);

            recordLedger(employee, leaveType, pool, LedgerEventType.HOLD_RELEASE,
                         releaseFromPool, pool.getAvailable(),
                         pool.getCycleStart(), request, policy, "SYSTEM",
                         "Hold released for leave request #" + request.getRequestId());

            remaining = remaining.subtract(releaseFromPool);
        }
    }

    /**
     * Deduct days on approval.  Converts hold -> used.
     * Draws from carry-forward first, then current cycle.
     */
    public void deductDays(Employee employee, LeaveType leaveType,
                           BigDecimal days, LeaveRequest request, LeavePolicy policy) {
        List<LeavePool> pools = getOrderedPools(employee.getEmployeeId(), leaveType);

        BigDecimal remaining = days;
        for (LeavePool pool : pools) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal heldInPool = pool.getHeld();
            if (heldInPool.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductFromPool = remaining.min(heldInPool);

            // Move from held -> used
            pool.setHeld(pool.getHeld().subtract(deductFromPool));
            pool.setUsed(pool.getUsed().add(deductFromPool));
            leavePoolDAO.save(pool);

            recordLedger(employee, leaveType, pool, LedgerEventType.DEDUCTION,
                         deductFromPool.negate(), pool.getAvailable(),
                         pool.getCycleStart(), request, policy, "SYSTEM",
                         "Deduction on approval of request #" + request.getRequestId());

            remaining = remaining.subtract(deductFromPool);
        }
    }

    /**
     * Credit back days when an approved leave is cancelled.
     */
    public void creditBackDays(Employee employee, LeaveType leaveType,
                               BigDecimal days, LeaveRequest request, LeavePolicy policy) {
        // Credit back to current cycle pool (simplest correct behavior)
        List<LeavePool> pools = getOrderedPools(employee.getEmployeeId(), leaveType);

        // Find the current cycle pool to credit back to
        LeavePool currentPool = pools.stream()
                .filter(p -> p.getPoolType() == PoolType.CURRENT_CYCLE)
                .findFirst()
                .orElseThrow(() -> LeaveManagementException.notFound(
                        "No current cycle pool found for credit-back"));

        currentPool.setUsed(currentPool.getUsed().subtract(days));
        leavePoolDAO.save(currentPool);

        recordLedger(employee, leaveType, currentPool, LedgerEventType.HOLD_RELEASE,
                     days, currentPool.getAvailable(),
                     currentPool.getCycleStart(), request, policy, "SYSTEM",
                     "Days credited back on cancellation of approved request #" + request.getRequestId());
    }

    /**
     * Get pools ordered for deduction: carry-forward first (earliest expiry),
     * then current cycle.
     */
    private List<LeavePool> getOrderedPools(Long employeeId, LeaveType leaveType) {
        List<LeavePool> pools = leavePoolDAO.findByEmployeeAndLeaveType(employeeId, leaveType);
        // Sort: CARRY_FORWARD first (ordered by expiry date), then CURRENT_CYCLE
        pools.sort(Comparator
                .comparing((LeavePool p) -> p.getPoolType() == PoolType.CURRENT_CYCLE ? 1 : 0)
                .thenComparing(p -> p.getExpiryDate() != null ? p.getExpiryDate() : LocalDate.MAX));
        return pools;
    }

    private void recordLedger(Employee employee, LeaveType leaveType, LeavePool pool,
                               LedgerEventType eventType, BigDecimal delta,
                               BigDecimal balanceAfter, LocalDate cycleStart,
                               LeaveRequest request, LeavePolicy policy,
                               String triggeredBy, String note) {
        LeaveLedgerEntry entry = LeaveLedgerEntry.builder()
                .employee(employee)
                .leaveType(leaveType)
                .poolType(pool.getPoolType())
                .eventType(eventType)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .cycleStart(cycleStart)
                .leaveRequest(request)
                .policy(policy)
                .triggeredBy(triggeredBy)
                .note(note)
                .build();
        ledgerDAO.save(entry);
    }
}
