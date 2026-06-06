package com.wfm.leave.dao;

import com.wfm.leave.model.EmployeePolicyEnrollment;
import com.wfm.leave.model.enums.LeaveType;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.util.List;
import java.util.Optional;

public class EmployeePolicyEnrollmentDAO extends AbstractDAO<EmployeePolicyEnrollment> {

    public EmployeePolicyEnrollmentDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public EmployeePolicyEnrollment save(EmployeePolicyEnrollment enrollment) {
        return persist(enrollment);
    }

    public Optional<EmployeePolicyEnrollment> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    /** All active enrollments for an employee */
    public List<EmployeePolicyEnrollment> findActiveByEmployee(Long employeeId) {
        return currentSession()
                .createQuery(
                    "FROM EmployeePolicyEnrollment WHERE employee.employeeId = :eid AND active = true",
                    EmployeePolicyEnrollment.class)
                .setParameter("eid", employeeId)
                .getResultList();
    }

    /** Find enrollment for a specific employee + policy group */
    public Optional<EmployeePolicyEnrollment> findByEmployeeAndPolicyGroup(
            Long employeeId, String policyGroupId) {
        return currentSession()
                .createQuery(
                    "FROM EmployeePolicyEnrollment WHERE employee.employeeId = :eid " +
                    "AND policyGroupId = :pgid AND active = true",
                    EmployeePolicyEnrollment.class)
                .setParameter("eid", employeeId)
                .setParameter("pgid", policyGroupId)
                .uniqueResultOptional();
    }

    /** All employees enrolled in a given policy group (for retroactive recomputation) */
    public List<EmployeePolicyEnrollment> findByPolicyGroupId(String policyGroupId) {
        return currentSession()
                .createQuery(
                    "FROM EmployeePolicyEnrollment WHERE policyGroupId = :pgid AND active = true",
                    EmployeePolicyEnrollment.class)
                .setParameter("pgid", policyGroupId)
                .getResultList();
    }
}
