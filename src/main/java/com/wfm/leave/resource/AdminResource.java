package com.wfm.leave.resource;

import com.wfm.leave.dao.*;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.service.CarryForwardService;
import com.wfm.leave.service.LeaveAccrualService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin resource for triggering accrual and carry-forward manually (for testing).
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private final LeaveAccrualService accrualService;
    private final CarryForwardService carryForwardService;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;
    private final EmployeeDAO employeeDAO;

    public AdminResource(LeaveAccrualService accrualService,
                         CarryForwardService carryForwardService,
                         EmployeePolicyEnrollmentDAO enrollmentDAO,
                         EmployeeDAO employeeDAO) {
        this.accrualService = accrualService;
        this.carryForwardService = carryForwardService;
        this.enrollmentDAO = enrollmentDAO;
        this.employeeDAO = employeeDAO;
    }

    /**
     * POST /api/admin/accrual?asOfDate=2026-06-06
     * Trigger accrual for ALL employees across all their enrolled policies.
     */
    @POST
    @Path("/accrual")
    @UnitOfWork
    public Response triggerAccrual(@QueryParam("asOfDate") String asOfDateStr) {
        LocalDate asOfDate = asOfDateStr != null
                ? LocalDate.parse(asOfDateStr)
                : LocalDate.now();

        List<Employee> allEmployees = employeeDAO.findAll();
        int processed = 0;

        for (Employee emp : allEmployees) {
            List<EmployeePolicyEnrollment> enrollments =
                    enrollmentDAO.findActiveByEmployee(emp.getEmployeeId());
            for (EmployeePolicyEnrollment enrollment : enrollments) {
                accrualService.processAccrual(emp, enrollment.getPolicy(), asOfDate);
                processed++;
            }
        }

        return Response.ok()
                .entity(new AccrualResult("Accrual completed", asOfDate.toString(), processed))
                .build();
    }

    /**
     * POST /api/admin/accrual/{employeeId}?asOfDate=2026-06-06
     * Trigger accrual for a single employee.
     */
    @POST
    @Path("/accrual/{employeeId}")
    @UnitOfWork
    public Response triggerAccrualForEmployee(@PathParam("employeeId") Long employeeId,
                                              @QueryParam("asOfDate") String asOfDateStr) {
        LocalDate asOfDate = asOfDateStr != null
                ? LocalDate.parse(asOfDateStr)
                : LocalDate.now();

        Employee emp = employeeDAO.findById(employeeId)
                .orElseThrow(() -> new WebApplicationException("Employee not found", 404));

        List<EmployeePolicyEnrollment> enrollments =
                enrollmentDAO.findActiveByEmployee(employeeId);
        for (EmployeePolicyEnrollment enrollment : enrollments) {
            accrualService.processAccrual(emp, enrollment.getPolicy(), asOfDate);
        }

        return Response.ok()
                .entity(new AccrualResult("Accrual completed for employee " + employeeId,
                        asOfDate.toString(), enrollments.size()))
                .build();
    }

    /**
     * POST /api/admin/carry-forward/expire?asOfDate=2026-06-06
     * Expire any carry-forward pools past their expiry date.
     */
    @POST
    @Path("/carry-forward/expire")
    @UnitOfWork
    public Response expireCarryForward(@QueryParam("asOfDate") String asOfDateStr) {
        LocalDate asOfDate = asOfDateStr != null
                ? LocalDate.parse(asOfDateStr)
                : LocalDate.now();

        carryForwardService.expireCarryForwardPools(asOfDate);
        return Response.ok()
                .entity(new AccrualResult("Carry-forward expiry processed", asOfDate.toString(), 0))
                .build();
    }

    public record AccrualResult(String message, String asOfDate, int policiesProcessed) {}
}
