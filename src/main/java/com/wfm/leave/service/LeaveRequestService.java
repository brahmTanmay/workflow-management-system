package com.wfm.leave.service;

import com.wfm.leave.dao.*;
import com.wfm.leave.exception.LeaveManagementException;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Orchestrates the leave request lifecycle:
 *   submit -> (PENDING + hold) -> approve/reject/cancel
 *
 * Concurrency: relies on optimistic locking in LeavePool (@Version)
 * to prevent double-deduction when two managers approve simultaneously.
 */
public class LeaveRequestService {

    private final LeaveRequestDAO requestDAO;
    private final EmployeeDAO employeeDAO;
    private final EmployeePolicyEnrollmentDAO enrollmentDAO;
    private final LeavePolicyDAO policyDAO;
    private final LeaveBalanceService balanceService;

    public LeaveRequestService(LeaveRequestDAO requestDAO,
                                EmployeeDAO employeeDAO,
                                EmployeePolicyEnrollmentDAO enrollmentDAO,
                                LeavePolicyDAO policyDAO,
                                LeaveBalanceService balanceService) {
        this.requestDAO = requestDAO;
        this.employeeDAO = employeeDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.policyDAO = policyDAO;
        this.balanceService = balanceService;
    }

    /**
     * Submit a new leave request.
     * - Validates the employee exists and is enrolled in a policy for this leave type
     * - Checks tenure eligibility
     * - Calculates number of days
     * - Places a hold on the balance
     * - Persists the request in PENDING status
     */
    public LeaveRequest submitRequest(Long employeeId, LeaveType leaveType,
                                       LocalDate startDate, LocalDate endDate,
                                       String reason) {
        Employee employee = employeeDAO.findById(employeeId)
                .orElseThrow(() -> LeaveManagementException.notFound("Employee not found: " + employeeId));

        // Find the active enrollment for this leave type
        EmployeePolicyEnrollment enrollment = findEnrollmentForLeaveType(employeeId, leaveType);
        LeavePolicy policy = enrollment.getPolicy();

        // Check tenure
        if (LeaveCycleCalculator.tenureYears(employee, LocalDate.now()) < policy.getMinTenureYears()) {
            throw LeaveManagementException.badRequest(
                    "Not yet eligible for " + leaveType + ". Required tenure: " +
                    policy.getMinTenureYears() + " years");
        }

        // Calculate working days (simple: endDate - startDate + 1, could be enhanced for holidays)
        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount <= 0) {
            throw LeaveManagementException.badRequest("End date must be on or after start date");
        }
        BigDecimal numberOfDays = BigDecimal.valueOf(dayCount);

        // Create the request
        LeaveRequest request = LeaveRequest.builder()
                .employee(employee)
                .leaveType(leaveType)
                .startDate(startDate)
                .endDate(endDate)
                .numberOfDays(numberOfDays)
                .status(LeaveRequestStatus.PENDING)
                .reason(reason)
                .build();
        request = requestDAO.save(request);

        // Place hold against the balance
        balanceService.placeHold(employee, leaveType, numberOfDays, request, policy);

        return request;
    }

    /**
     * Approve a pending leave request.
     * - Validates the approver is the employee's manager
     * - Converts held days to deducted days
     */
    public LeaveRequest approveRequest(Long requestId, Long approverId) {
        LeaveRequest request = requestDAO.findById(requestId)
                .orElseThrow(() -> LeaveManagementException.notFound("Leave request not found: " + requestId));

        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw LeaveManagementException.badRequest(
                    "Cannot approve request in status: " + request.getStatus());
        }

        Employee approver = employeeDAO.findById(approverId)
                .orElseThrow(() -> LeaveManagementException.notFound("Approver not found: " + approverId));

        // Verify approver is the employee's manager
        Employee employee = request.getEmployee();
        if (employee.getManager() == null ||
            !employee.getManager().getEmployeeId().equals(approverId)) {
            throw LeaveManagementException.forbidden(
                    "Only the employee's direct manager can approve leave requests");
        }

        EmployeePolicyEnrollment enrollment =
                findEnrollmentForLeaveType(employee.getEmployeeId(), request.getLeaveType());

        // Convert hold -> deduction
        balanceService.deductDays(employee, request.getLeaveType(),
                                  request.getNumberOfDays(), request, enrollment.getPolicy());

        request.setStatus(LeaveRequestStatus.APPROVED);
        request.setActionedBy(approver);
        return requestDAO.save(request);
    }

    /**
     * Reject a pending leave request.
     * - Releases held days back to the pool.
     */
    public LeaveRequest rejectRequest(Long requestId, Long rejecterId, String rejectionReason) {
        LeaveRequest request = requestDAO.findById(requestId)
                .orElseThrow(() -> LeaveManagementException.notFound("Leave request not found: " + requestId));

        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw LeaveManagementException.badRequest(
                    "Cannot reject request in status: " + request.getStatus());
        }

        Employee rejecter = employeeDAO.findById(rejecterId)
                .orElseThrow(() -> LeaveManagementException.notFound("Rejecter not found: " + rejecterId));

        Employee employee = request.getEmployee();
        if (employee.getManager() == null ||
            !employee.getManager().getEmployeeId().equals(rejecterId)) {
            throw LeaveManagementException.forbidden(
                    "Only the employee's direct manager can reject leave requests");
        }

        EmployeePolicyEnrollment enrollment =
                findEnrollmentForLeaveType(employee.getEmployeeId(), request.getLeaveType());

        // Release the hold
        balanceService.releaseHold(employee, request.getLeaveType(),
                                   request.getNumberOfDays(), request, enrollment.getPolicy());

        request.setStatus(LeaveRequestStatus.REJECTED);
        request.setActionedBy(rejecter);
        request.setRejectionReason(rejectionReason);
        return requestDAO.save(request);
    }

    /**
     * Cancel a leave request.
     * - If PENDING: releases the hold.
     * - If APPROVED: credits back the deducted days.
     */
    public LeaveRequest cancelRequest(Long requestId, Long cancelledById) {
        LeaveRequest request = requestDAO.findById(requestId)
                .orElseThrow(() -> LeaveManagementException.notFound("Leave request not found: " + requestId));

        Employee employee = request.getEmployee();

        // Only the employee themselves or their manager can cancel
        if (!employee.getEmployeeId().equals(cancelledById) &&
            (employee.getManager() == null ||
             !employee.getManager().getEmployeeId().equals(cancelledById))) {
            throw LeaveManagementException.forbidden(
                    "Only the employee or their manager can cancel a leave request");
        }

        EmployeePolicyEnrollment enrollment =
                findEnrollmentForLeaveType(employee.getEmployeeId(), request.getLeaveType());

        switch (request.getStatus()) {
            case PENDING -> {
                // Release the hold
                balanceService.releaseHold(employee, request.getLeaveType(),
                                           request.getNumberOfDays(), request, enrollment.getPolicy());
            }
            case APPROVED -> {
                // Credit back used days
                balanceService.creditBackDays(employee, request.getLeaveType(),
                                              request.getNumberOfDays(), request, enrollment.getPolicy());
            }
            default -> throw LeaveManagementException.badRequest(
                    "Cannot cancel request in status: " + request.getStatus());
        }

        Employee canceller = employeeDAO.findById(cancelledById)
                .orElseThrow(() -> LeaveManagementException.notFound("User not found: " + cancelledById));

        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setActionedBy(canceller);
        return requestDAO.save(request);
    }

    /** Get all pending requests that a manager needs to act on */
    public List<LeaveRequest> getPendingRequestsForManager(Long managerId) {
        return requestDAO.findPendingForManager(managerId);
    }

    /** Get all requests for an employee */
    public List<LeaveRequest> getRequestsForEmployee(Long employeeId) {
        return requestDAO.findByEmployee(employeeId);
    }

    // -- helpers --

    private EmployeePolicyEnrollment findEnrollmentForLeaveType(Long employeeId, LeaveType leaveType) {
        return enrollmentDAO.findActiveByEmployee(employeeId).stream()
                .filter(e -> e.getLeaveType() == leaveType)
                .findFirst()
                .orElseThrow(() -> LeaveManagementException.notFound(
                        "Employee " + employeeId + " is not enrolled in any policy for " + leaveType));
    }
}
