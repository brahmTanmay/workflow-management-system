package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds the database with test data on startup:
 *
 * Team: "Engineering"
 *   - Employee 1: Alice (Manager, no manager above her)
 *   - Employee 2: Bob   (reports to Alice)
 *   - Employee 3: Carol (reports to Alice)
 *
 * Policies:
 *   - VACATION: 25 days/year, monthly accrual, carry-forward up to 5 days (expires in 3 months)
 *   - SICK:     12 days/year, yearly grant upfront, no carry-forward
 *   - PARENTAL: 90 days one-time grant, no carry-forward
 *
 * All employees enrolled in VACATION and SICK.
 * Bob also enrolled in PARENTAL.
 */
public class DataSeeder {

    private final SessionFactory sessionFactory;
    private final TeamDAO teamDAO;
    private final EmployeeDAO employeeDAO;
    private final LeavePolicyDAO policyDAO;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;

    public DataSeeder(SessionFactory sessionFactory,
                      TeamDAO teamDAO,
                      EmployeeDAO employeeDAO,
                      LeavePolicyDAO policyDAO,
                      EmployeePolicyEnrollmentDAO enrollmentDAO) {
        this.sessionFactory = sessionFactory;
        this.teamDAO = teamDAO;
        this.employeeDAO = employeeDAO;
        this.policyDAO = policyDAO;
        this.enrollmentDAO = enrollmentDAO;
    }

    public void seed() {
        Session session = sessionFactory.openSession();
        try {
            ManagedSessionContext.bind(session);
            Transaction tx = session.beginTransaction();

            // -- Team --
            Team engineering = teamDAO.save(Team.builder()
                    .teamName("Engineering")
                    .build());

            // -- Employees --
            Employee alice = employeeDAO.save(Employee.builder()
                    .employeeName("Alice Johnson")
                    .email("alice@wfm.com")
                    .phoneNumber("+1-555-0101")
                    .team(engineering)
                    .manager(null) // top-level manager
                    .startDate(LocalDate.of(2022, 3, 15))
                    .build());

            Employee bob = employeeDAO.save(Employee.builder()
                    .employeeName("Bob Smith")
                    .email("bob@wfm.com")
                    .phoneNumber("+1-555-0102")
                    .team(engineering)
                    .manager(alice)
                    .startDate(LocalDate.of(2023, 6, 1))
                    .build());

            Employee carol = employeeDAO.save(Employee.builder()
                    .employeeName("Carol Williams")
                    .email("carol@wfm.com")
                    .phoneNumber("+1-555-0103")
                    .team(engineering)
                    .manager(alice)
                    .startDate(LocalDate.of(2024, 1, 10))
                    .build());

            // -- Policies --
            LeavePolicy vacationPolicy = policyDAO.save(LeavePolicy.builder()
                    .policyGroupId("POL-VACATION")
                    .version(1)
                    .policyName("Standard Vacation")
                    .leaveType(LeaveType.VACATION)
                    .accrualMethod(AccrualMethod.MONTHLY)
                    .entitlementDays(new BigDecimal("25.00"))
                    .cycleType(CycleType.CALENDAR_YEAR)
                    .carryForwardEnabled(true)
                    .carryForwardMaxDays(new BigDecimal("5.00"))
                    .carryForwardExpiryMonths(3)
                    .minTenureYears(0)
                    .active(true)
                    .build());

            LeavePolicy sickPolicy = policyDAO.save(LeavePolicy.builder()
                    .policyGroupId("POL-SICK")
                    .version(1)
                    .policyName("Standard Sick Leave")
                    .leaveType(LeaveType.SICK)
                    .accrualMethod(AccrualMethod.YEARLY)
                    .entitlementDays(new BigDecimal("12.00"))
                    .cycleType(CycleType.CALENDAR_YEAR)
                    .carryForwardEnabled(false)
                    .minTenureYears(0)
                    .active(true)
                    .build());

            LeavePolicy parentalPolicy = policyDAO.save(LeavePolicy.builder()
                    .policyGroupId("POL-PARENTAL")
                    .version(1)
                    .policyName("Parental Leave")
                    .leaveType(LeaveType.PARENTAL)
                    .accrualMethod(AccrualMethod.ONE_TIME)
                    .entitlementDays(new BigDecimal("90.00"))
                    .cycleType(CycleType.EMPLOYEE_START_DATE)
                    .carryForwardEnabled(false)
                    .minTenureYears(0)
                    .active(true)
                    .build());

            // -- Enrollments --
            // Alice: VACATION + SICK
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(alice).policyGroupId("POL-VACATION").leaveType(LeaveType.VACATION)
                    .policy(vacationPolicy).effectiveFrom(alice.getStartDate()).active(true).build());
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(alice).policyGroupId("POL-SICK").leaveType(LeaveType.SICK)
                    .policy(sickPolicy).effectiveFrom(alice.getStartDate()).active(true).build());

            // Bob: VACATION + SICK + PARENTAL
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(bob).policyGroupId("POL-VACATION").leaveType(LeaveType.VACATION)
                    .policy(vacationPolicy).effectiveFrom(bob.getStartDate()).active(true).build());
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(bob).policyGroupId("POL-SICK").leaveType(LeaveType.SICK)
                    .policy(sickPolicy).effectiveFrom(bob.getStartDate()).active(true).build());
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(bob).policyGroupId("POL-PARENTAL").leaveType(LeaveType.PARENTAL)
                    .policy(parentalPolicy).effectiveFrom(bob.getStartDate()).active(true).build());

            // Carol: VACATION + SICK
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(carol).policyGroupId("POL-VACATION").leaveType(LeaveType.VACATION)
                    .policy(vacationPolicy).effectiveFrom(carol.getStartDate()).active(true).build());
            enrollmentDAO.save(EmployeePolicyEnrollment.builder()
                    .employee(carol).policyGroupId("POL-SICK").leaveType(LeaveType.SICK)
                    .policy(sickPolicy).effectiveFrom(carol.getStartDate()).active(true).build());

            tx.commit();
            System.out.println("=== DATA SEEDED SUCCESSFULLY ===");
            System.out.println("  Team: Engineering");
            System.out.println("  Alice (ID=1, Manager), Bob (ID=2), Carol (ID=3)");
            System.out.println("  Policies: VACATION (25d/yr monthly), SICK (12d/yr yearly), PARENTAL (90d one-time)");
        } catch (Exception e) {
            System.err.println("Seed failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ManagedSessionContext.unbind(sessionFactory);
            session.close();
        }
    }
}
