package com.wfm.leave.dao;

import com.wfm.leave.model.LeaveLedgerEntry;
import com.wfm.leave.model.enums.LeaveType;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.time.Instant;
import java.util.List;

public class LeaveLedgerDAO extends AbstractDAO<LeaveLedgerEntry> {

    public LeaveLedgerDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public LeaveLedgerEntry save(LeaveLedgerEntry entry) {
        return persist(entry);
    }

    /** Full audit trail for an employee and leave type, chronologically ordered */
    public List<LeaveLedgerEntry> findByEmployeeAndLeaveType(Long employeeId, LeaveType leaveType) {
        return currentSession()
                .createQuery(
                    "FROM LeaveLedgerEntry WHERE employee.employeeId = :eid AND leaveType = :lt " +
                    "ORDER BY createdAt ASC, ledgerId ASC",
                    LeaveLedgerEntry.class)
                .setParameter("eid", employeeId)
                .setParameter("lt", leaveType)
                .getResultList();
    }

    /** Full audit trail for an employee across all leave types */
    public List<LeaveLedgerEntry> findByEmployee(Long employeeId) {
        return currentSession()
                .createQuery(
                    "FROM LeaveLedgerEntry WHERE employee.employeeId = :eid " +
                    "ORDER BY createdAt ASC, ledgerId ASC",
                    LeaveLedgerEntry.class)
                .setParameter("eid", employeeId)
                .getResultList();
    }

    /** Reconstruct balance at a specific point in time */
    public List<LeaveLedgerEntry> findByEmployeeAndLeaveTypeUpTo(
            Long employeeId, LeaveType leaveType, Instant upTo) {
        return currentSession()
                .createQuery(
                    "FROM LeaveLedgerEntry WHERE employee.employeeId = :eid AND leaveType = :lt " +
                    "AND createdAt <= :upTo ORDER BY createdAt ASC, ledgerId ASC",
                    LeaveLedgerEntry.class)
                .setParameter("eid", employeeId)
                .setParameter("lt", leaveType)
                .setParameter("upTo", upTo)
                .getResultList();
    }

    /** Ledger entries linked to a specific leave request */
    public List<LeaveLedgerEntry> findByLeaveRequest(Long leaveRequestId) {
        return currentSession()
                .createQuery(
                    "FROM LeaveLedgerEntry WHERE leaveRequest.requestId = :rid " +
                    "ORDER BY createdAt ASC",
                    LeaveLedgerEntry.class)
                .setParameter("rid", leaveRequestId)
                .getResultList();
    }
}
