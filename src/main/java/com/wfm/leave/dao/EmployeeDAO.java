package com.wfm.leave.dao;

import com.wfm.leave.model.Employee;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import java.util.List;
import java.util.Optional;

public class EmployeeDAO extends AbstractDAO<Employee> {

    public EmployeeDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Employee save(Employee employee) {
        return persist(employee);
    }

    public Optional<Employee> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    public List<Employee> findByManagerId(Long managerId) {
        return currentSession()
                .createQuery("FROM Employee WHERE manager.employeeId = :managerId", Employee.class)
                .setParameter("managerId", managerId)
                .getResultList();
    }

    public List<Employee> findAll() {
        return currentSession()
                .createQuery("FROM Employee", Employee.class)
                .getResultList();
    }

    public List<Employee> findByTeamId(Long teamId) {
        return currentSession()
                .createQuery("FROM Employee WHERE team.teamId = :teamId", Employee.class)
                .setParameter("teamId", teamId)
                .getResultList();
    }
}
