package com.wfm.leave.dao;

import com.wfm.leave.model.LeavePolicy;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.util.List;
import java.util.Optional;

public class LeavePolicyDAO extends AbstractDAO<LeavePolicy> {

    public LeavePolicyDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public LeavePolicy save(LeavePolicy policy) {
        return persist(policy);
    }

    public Optional<LeavePolicy> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    /** Get the currently active version of a policy group */
    public Optional<LeavePolicy> findActiveByGroupId(String policyGroupId) {
        return currentSession()
                .createQuery(
                    "FROM LeavePolicy WHERE policyGroupId = :gid AND active = true",
                    LeavePolicy.class)
                .setParameter("gid", policyGroupId)
                .uniqueResultOptional();
    }

    /** Get all versions of a policy group, ordered by version ascending */
    public List<LeavePolicy> findAllVersionsByGroupId(String policyGroupId) {
        return currentSession()
                .createQuery(
                    "FROM LeavePolicy WHERE policyGroupId = :gid ORDER BY version ASC",
                    LeavePolicy.class)
                .setParameter("gid", policyGroupId)
                .getResultList();
    }

    /** Get a specific version */
    public Optional<LeavePolicy> findByGroupIdAndVersion(String policyGroupId, int version) {
        return currentSession()
                .createQuery(
                    "FROM LeavePolicy WHERE policyGroupId = :gid AND version = :ver",
                    LeavePolicy.class)
                .setParameter("gid", policyGroupId)
                .setParameter("ver", version)
                .uniqueResultOptional();
    }
}
