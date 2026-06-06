package com.wfm.leave.resource;

import com.wfm.leave.dao.*;
import com.wfm.leave.dto.*;
import com.wfm.leave.model.*;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.service.LeaveBalanceService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/employees/{employeeId}/balance")
@Produces(MediaType.APPLICATION_JSON)
public class BalanceResource {

    private final LeavePoolDAO leavePoolDAO;
    private final LeaveBalanceService balanceService;

    public BalanceResource(LeavePoolDAO leavePoolDAO, LeaveBalanceService balanceService) {
        this.leavePoolDAO = leavePoolDAO;
        this.balanceService = balanceService;
    }

    /**
     * GET /api/employees/{employeeId}/balance
     * Returns all balance summaries grouped by leave type.
     */
    @GET
    @UnitOfWork
    public List<BalanceSummaryDTO> getBalance(@PathParam("employeeId") Long employeeId) {
        List<LeavePool> pools = leavePoolDAO.findByEmployee(employeeId);

        // Group by leave type
        Map<LeaveType, List<LeavePool>> grouped = pools.stream()
                .collect(Collectors.groupingBy(LeavePool::getLeaveType));

        List<BalanceSummaryDTO> summaries = new ArrayList<>();
        for (Map.Entry<LeaveType, List<LeavePool>> entry : grouped.entrySet()) {
            List<BalanceDTO> poolDTOs = entry.getValue().stream()
                    .map(this::toBalanceDTO)
                    .toList();

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
                    .pools(poolDTOs)
                    .build());
        }
        return summaries;
    }

    /**
     * GET /api/employees/{employeeId}/balance/{leaveType}
     * Returns balance for a specific leave type.
     */
    @GET
    @Path("/{leaveType}")
    @UnitOfWork
    public BalanceSummaryDTO getBalanceByType(@PathParam("employeeId") Long employeeId,
                                              @PathParam("leaveType") LeaveType leaveType) {
        List<LeavePool> pools = leavePoolDAO.findByEmployeeAndLeaveType(employeeId, leaveType);

        List<BalanceDTO> poolDTOs = pools.stream().map(this::toBalanceDTO).toList();
        BigDecimal totalAvailable = pools.stream()
                .map(LeavePool::getAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredited = pools.stream()
                .map(LeavePool::getCredited).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUsed = pools.stream()
                .map(LeavePool::getUsed).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHeld = pools.stream()
                .map(LeavePool::getHeld).reduce(BigDecimal.ZERO, BigDecimal::add);

        return BalanceSummaryDTO.builder()
                .leaveType(leaveType)
                .totalAvailable(totalAvailable)
                .totalCredited(totalCredited)
                .totalUsed(totalUsed)
                .totalHeld(totalHeld)
                .pools(poolDTOs)
                .build();
    }

    private BalanceDTO toBalanceDTO(LeavePool pool) {
        return BalanceDTO.builder()
                .leaveType(pool.getLeaveType())
                .poolType(pool.getPoolType())
                .credited(pool.getCredited())
                .used(pool.getUsed())
                .held(pool.getHeld())
                .available(pool.getAvailable())
                .cycleStart(pool.getCycleStart())
                .cycleEnd(pool.getCycleEnd())
                .expiryDate(pool.getExpiryDate())
                .build();
    }
}
