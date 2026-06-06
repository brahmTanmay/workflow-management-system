package com.wfm.leave.resource;

import com.wfm.leave.dao.LeaveLedgerDAO;
import com.wfm.leave.dto.LedgerEntryDTO;
import com.wfm.leave.model.LeaveLedgerEntry;
import com.wfm.leave.model.enums.LeaveType;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;

/**
 * Audit trail endpoint — fully reconstructable history of all balance mutations.
 */
@Path("/api/employees/{employeeId}/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    private final LeaveLedgerDAO ledgerDAO;

    public AuditResource(LeaveLedgerDAO ledgerDAO) {
        this.ledgerDAO = ledgerDAO;
    }

    /**
     * GET /api/employees/{employeeId}/audit
     * Full audit trail across all leave types.
     */
    @GET
    @UnitOfWork
    public List<LedgerEntryDTO> getAuditTrail(@PathParam("employeeId") Long employeeId) {
        return ledgerDAO.findByEmployee(employeeId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * GET /api/employees/{employeeId}/audit/{leaveType}
     * Audit trail for a specific leave type.
     */
    @GET
    @Path("/{leaveType}")
    @UnitOfWork
    public List<LedgerEntryDTO> getAuditTrailByType(@PathParam("employeeId") Long employeeId,
                                                     @PathParam("leaveType") LeaveType leaveType) {
        return ledgerDAO.findByEmployeeAndLeaveType(employeeId, leaveType).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * GET /api/employees/{employeeId}/audit/{leaveType}/at?timestamp=...
     * Reconstruct balance at a specific point in time.
     */
    @GET
    @Path("/{leaveType}/at")
    @UnitOfWork
    public List<LedgerEntryDTO> getAuditTrailAt(@PathParam("employeeId") Long employeeId,
                                                 @PathParam("leaveType") LeaveType leaveType,
                                                 @QueryParam("timestamp") String timestamp) {
        Instant upTo = Instant.parse(timestamp);
        return ledgerDAO.findByEmployeeAndLeaveTypeUpTo(employeeId, leaveType, upTo).stream()
                .map(this::toDTO)
                .toList();
    }

    private LedgerEntryDTO toDTO(LeaveLedgerEntry entry) {
        return LedgerEntryDTO.builder()
                .ledgerId(entry.getLedgerId())
                .leaveType(entry.getLeaveType())
                .poolType(entry.getPoolType())
                .eventType(entry.getEventType())
                .delta(entry.getDelta())
                .balanceAfter(entry.getBalanceAfter())
                .cycleStart(entry.getCycleStart())
                .leaveRequestId(entry.getLeaveRequest() != null
                        ? entry.getLeaveRequest().getRequestId() : null)
                .triggeredBy(entry.getTriggeredBy())
                .note(entry.getNote())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
