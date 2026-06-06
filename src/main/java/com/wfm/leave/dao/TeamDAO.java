package com.wfm.leave.dao;

import com.wfm.leave.model.Team;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.util.Optional;

public class TeamDAO extends AbstractDAO<Team> {

    public TeamDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Team save(Team team) {
        return persist(team);
    }

    public Optional<Team> findById(Long id) {
        return Optional.ofNullable(get(id));
    }
}
