package com.wfm.leave.resource;

import com.wfm.leave.dto.LeaveActionDTO;
import com.wfm.leave.dto.LeaveRequestDTO;
import com.wfm.leave.model.LeaveRequest;
import com.wfm.leave.service.LeaveRequestService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

@Path("/api/employees/{employeeId}/leave-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LeaveRequestResource {

    private final LeaveRequestService requestService;

    public LeaveRequestResource(LeaveRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * POST /api/employees/{employeeId}/leave-requests
     * Submit a new leave request.
     */
    @POST
    @UnitOfWork
    public Response submitRequest(@PathParam("employeeId") Long employeeId,
                                   @Valid LeaveRequestDTO dto) {
        LeaveRequest request = requestService.submitRequest(
                employeeId, dto.getLeaveType(),
                dto.getStartDate(), dto.getEndDate(),
                dto.getReason());

        return Response.created(URI.create("/api/employees/" + employeeId +
                "/leave-requests/" + request.getRequestId()))
                .entity(request)
                .build();
    }

    /**
     * GET /api/employees/{employeeId}/leave-requests
     * List all leave requests for an employee.
     */
    @GET
    @UnitOfWork
    public List<LeaveRequest> listRequests(@PathParam("employeeId") Long employeeId) {
        return requestService.getRequestsForEmployee(employeeId);
    }

    /**
     * POST /api/employees/{employeeId}/leave-requests/{requestId}/approve
     */
    @POST
    @Path("/{requestId}/approve")
    @UnitOfWork
    public LeaveRequest approve(@PathParam("employeeId") Long employeeId,
                                 @PathParam("requestId") Long requestId,
                                 @Valid LeaveActionDTO dto) {
        return requestService.approveRequest(requestId, dto.getActionedById());
    }

    /**
     * POST /api/employees/{employeeId}/leave-requests/{requestId}/reject
     */
    @POST
    @Path("/{requestId}/reject")
    @UnitOfWork
    public LeaveRequest reject(@PathParam("employeeId") Long employeeId,
                                @PathParam("requestId") Long requestId,
                                @Valid LeaveActionDTO dto) {
        return requestService.rejectRequest(requestId, dto.getActionedById(), dto.getReason());
    }

    /**
     * POST /api/employees/{employeeId}/leave-requests/{requestId}/cancel
     */
    @POST
    @Path("/{requestId}/cancel")
    @UnitOfWork
    public LeaveRequest cancel(@PathParam("employeeId") Long employeeId,
                                @PathParam("requestId") Long requestId,
                                @Valid LeaveActionDTO dto) {
        return requestService.cancelRequest(requestId, dto.getActionedById());
    }
}
