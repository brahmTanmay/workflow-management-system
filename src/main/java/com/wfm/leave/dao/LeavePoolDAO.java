package com.wfm.leave.dao;

import com.wfm.leave.model.LeavePool;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.model.enums.PoolType;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class LeavePoolDAO extends AbstractDAO<LeavePool> {

    public LeavePoolDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public LeavePool save(LeavePool pool) {
        return persist(pool);
    }

    public Optional<LeavePool> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    /** All active pools for an employee and leave type (current cycle + carry-forward) */
    public List<LeavePool> findByEmployeeAndLeaveType(Long employeeId, LeaveType leaveType) {
        return currentSession()
                .createQuery(
                    "FROM LeavePool WHERE employee.employeeId = :eid AND leaveType = :lt " +
                    "ORDER BY poolType ASC, cycleStart ASC",
                    LeavePool.class)
                .setParameter("eid", employeeId)
                .setParameter("lt", leaveType)
                .getResultList();
    }

    /** All pools for an employee across all leave types */
    public List<LeavePool> findByEmployee(Long employeeId) {
        return currentSession()
                .createQuery(
                    "FROM LeavePool WHERE employee.employeeId = :eid ORDER BY leaveType, poolType",
                    LeavePool.class)
                .setParameter("eid", employeeId)
                .getResultList();
    }

    /** Find a specific pool */
    public Optional<LeavePool> findByEmployeeLeaveTypePoolCycle(
            Long employeeId, LeaveType leaveType, PoolType poolType, LocalDate cycleStart) {
        return currentSession()
                .createQuery(
                    "FROM LeavePool WHERE employee.employeeId = :eid AND leaveType = :lt " +
                    "AND poolType = :pt AND cycleStart = :cs",
                    LeavePool.class)
                .setParameter("eid", employeeId)
                .setParameter("lt", leaveType)
                .setParameter("pt", poolType)
                .setParameter("cs", cycleStart)
                .uniqueResultOptional();
    }

    /** Find carry-forward pools that have expired */
    public List<LeavePool> findExpiredCarryForwardPools(LocalDate asOfDate) {
        return currentSession()
                .createQuery(
                    "FROM LeavePool WHERE poolType = :pt AND expiryDate IS NOT NULL " +
                    "AND expiryDate <= :asOf AND (credited - used - held) > 0",
                    LeavePool.class)
                .setParameter("pt", PoolType.CARRY_FORWARD)
                .setParameter("asOf", asOfDate)
                .getResultList();
    }
}
