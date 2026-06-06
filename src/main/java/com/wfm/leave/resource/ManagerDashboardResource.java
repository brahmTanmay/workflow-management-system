package com.wfm.leave.resource;

import com.wfm.leave.dao.*;
import com.wfm.leave.dto.*;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.service.LeaveBalanceService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager dashboard: view balances and pending requests for all direct reports.
 */
@Path("/api/managers/{managerId}/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class ManagerDashboardResource {

    private final EmployeeDAO employeeDAO;
    private final LeavePoolDAO leavePoolDAO;
    private final LeaveBalanceService balanceService;
    private final com.wfm.leave.service.LeaveRequestService requestService;

    public ManagerDashboardResource(EmployeeDAO employeeDAO,
                                     LeavePoolDAO leavePoolDAO,
                                     LeaveBalanceService balanceService,
                                     com.wfm.leave.service.LeaveRequestService requestService) {
        this.employeeDAO = employeeDAO;
        this.leavePoolDAO = leavePoolDAO;
        this.balanceService = balanceService;
        this.requestService = requestService;
    }

    /**
     * GET /api/managers/{managerId}/dashboard/balances
     * Returns leave balances for all direct reports.
     */
    @GET
    @Path("/balances")
    @UnitOfWork
    public List<TeamBalanceDTO> getTeamBalances(@PathParam("managerId") Long managerId) {
        List<Employee> directReports = employeeDAO.findByManagerId(managerId);
        List<TeamBalanceDTO> result = new ArrayList<>();

        for (Employee emp : directReports) {
            List<LeavePool> pools = leavePoolDAO.findByEmployee(emp.getEmployeeId());

            Map<LeaveType, List<LeavePool>> grouped = pools.stream()
                    .collect(Collectors.groupingBy(LeavePool::getLeaveType));

            List<BalanceSummaryDTO> summaries = new ArrayList<>();
            for (Map.Entry<LeaveType, List<LeavePool>> entry : grouped.entrySet()) {
                BigDecimal totalAvailable = entry.getValue().stream()
                        .map(LeavePool::getAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalCredited = entry.getValue().stream()
                        .map(LeavePool::getCredited).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalUsed = entry.getValue().stream()
                        .map(LeavePool::getUsed).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalHeld = entry.getValue().stream()
                        .map(LeavePool::getHeld).reduce(BigDecimal.ZERO, BigDecimal::add);

                summaries.add(BalanceSummaryDTO.builder()
                        .leaveType(entry.getKey())
                        .totalAvailable(totalAvailable)
                        .totalCredited(totalCredited)
                        .totalUsed(totalUsed)
                        .totalHeld(totalHeld)
                        .build());
            }

            result.add(TeamBalanceDTO.builder()
                    .employeeId(emp.getEmployeeId())
                    .employeeName(emp.getEmployeeName())
                    .balances(summaries)
                    .build());
        }
        return result;
    }

    /**
     * GET /api/managers/{managerId}/dashboard/pending-requests
     * Returns all pending leave requests from direct reports.
     */
    @GET
    @Path("/pending-requests")
    @UnitOfWork
    public List<LeaveRequest> getPendingRequests(@PathParam("managerId") Long managerId) {
        return requestService.getPendingRequestsForManager(managerId);
    }
}
