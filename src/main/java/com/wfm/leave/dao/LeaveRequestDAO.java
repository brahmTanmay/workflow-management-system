package com.wfm.leave.dao;

import com.wfm.leave.model.LeaveRequest;
import com.wfm.leave.model.enums.LeaveRequestStatus;
import com.wfm.leave.model.enums.LeaveType;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.util.List;
import java.util.Optional;

public class LeaveRequestDAO extends AbstractDAO<LeaveRequest> {

    public LeaveRequestDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public LeaveRequest save(LeaveRequest request) {
        return persist(request);
    }

    public Optional<LeaveRequest> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    public List<LeaveRequest> findByEmployee(Long employeeId) {
        return currentSession()
                .createQuery(
                    "FROM LeaveRequest WHERE employee.employeeId = :eid ORDER BY createdAt DESC",
                    LeaveRequest.class)
                .setParameter("eid", employeeId)
                .getResultList();
    }

    public List<LeaveRequest> findByEmployeeAndStatus(Long employeeId, LeaveRequestStatus status) {
        return currentSession()
                .createQuery(
                    "FROM LeaveRequest WHERE employee.employeeId = :eid AND status = :st " +
                    "ORDER BY createdAt DESC",
                    LeaveRequest.class)
                .setParameter("eid", employeeId)
                .setParameter("st", status)
                .getResultList();
    }

    /** Pending requests for all direct reports of a manager */
    public List<LeaveRequest> findPendingForManager(Long managerId) {
        return currentSession()
                .createQuery(
                    "FROM LeaveRequest lr WHERE lr.employee.manager.employeeId = :mid " +
                    "AND lr.status = :st ORDER BY lr.createdAt ASC",
                    LeaveRequest.class)
                .setParameter("mid", managerId)
                .setParameter("st", LeaveRequestStatus.PENDING)
                .getResultList();
    }

    /** Approved requests for an employee and leave type in a given cycle */
    public List<LeaveRequest> findApprovedByEmployeeAndType(Long employeeId, LeaveType leaveType) {
        return currentSession()
                .createQuery(
                    "FROM LeaveRequest WHERE employee.employeeId = :eid AND leaveType = :lt " +
                    "AND status = :st ORDER BY startDate ASC",
                    LeaveRequest.class)
                .setParameter("eid", employeeId)
                .setParameter("lt", leaveType)
                .setParameter("st", LeaveRequestStatus.APPROVED)
                .getResultList();
    }
}
