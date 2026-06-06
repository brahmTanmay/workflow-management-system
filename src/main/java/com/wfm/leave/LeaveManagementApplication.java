package com.wfm.leave;

import com.wfm.leave.config.LeaveManagementConfiguration;
import com.wfm.leave.dao.*;
import com.wfm.leave.model.*;
import com.wfm.leave.resource.*;
import com.wfm.leave.service.*;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;

public class LeaveManagementApplication extends Application<LeaveManagementConfiguration> {

    /**
     * Hibernate bundle — registers all JPA entities.
     */
    private final HibernateBundle<LeaveManagementConfiguration> hibernateBundle =
            new HibernateBundle<>(
                    Team.class,
                    Employee.class,
                    LeavePolicy.class,
                    LeavePool.class,
                    LeaveRequest.class,
                    EmployeePolicyEnrollment.class,
                    LeaveLedgerEntry.class
            ) {
                @Override
                public DataSourceFactory getDataSourceFactory(LeaveManagementConfiguration config) {
                    return config.getDatabase();
                }
            };

    public static void main(String[] args) throws Exception {
        new LeaveManagementApplication().run(args);
    }

    @Override
    public String getName() {
        return "leave-management";
    }

    @Override
    public void initialize(Bootstrap<LeaveManagementConfiguration> bootstrap) {
        bootstrap.addBundle(hibernateBundle);
    }

    @Override
    public void run(LeaveManagementConfiguration config, Environment environment) {
        // -- DAOs --
        final var sessionFactory = hibernateBundle.getSessionFactory();
        final EmployeeDAO employeeDAO = new EmployeeDAO(sessionFactory);
        final TeamDAO teamDAO = new TeamDAO(sessionFactory);
        final LeavePolicyDAO policyDAO = new LeavePolicyDAO(sessionFactory);
        final LeavePoolDAO leavePoolDAO = new LeavePoolDAO(sessionFactory);
        final LeaveRequestDAO requestDAO = new LeaveRequestDAO(sessionFactory);
        final EmployeePolicyEnrollmentDAO enrollmentDAO = new EmployeePolicyEnrollmentDAO(sessionFactory);
        final LeaveLedgerDAO ledgerDAO = new LeaveLedgerDAO(sessionFactory);

        // -- Services --
        final LeaveBalanceService balanceService = new LeaveBalanceService(leavePoolDAO, ledgerDAO);
        final LeaveAccrualService accrualService = new LeaveAccrualService(leavePoolDAO, ledgerDAO, enrollmentDAO);
        final LeaveRequestService requestService = new LeaveRequestService(
                requestDAO, employeeDAO, enrollmentDAO, policyDAO, balanceService);
        final CarryForwardService carryForwardService = new CarryForwardService(leavePoolDAO, ledgerDAO, enrollmentDAO);
        final PolicyChangeService policyChangeService = new PolicyChangeService(
                policyDAO, leavePoolDAO, ledgerDAO, enrollmentDAO);

        // -- JAX-RS Resources --
        environment.jersey().register(new BalanceResource(leavePoolDAO, balanceService));
        environment.jersey().register(new LeaveRequestResource(requestService));
        environment.jersey().register(new ManagerDashboardResource(
                employeeDAO, leavePoolDAO, balanceService, requestService));
        environment.jersey().register(new AuditResource(ledgerDAO));
        environment.jersey().register(new AdminResource(
                accrualService, carryForwardService, enrollmentDAO, employeeDAO));

        // -- Seed test data --
        new DataSeeder(sessionFactory, teamDAO, employeeDAO, policyDAO, enrollmentDAO).seed();
    }
}
