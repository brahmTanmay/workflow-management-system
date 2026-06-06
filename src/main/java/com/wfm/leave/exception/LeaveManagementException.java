package com.wfm.leave.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class LeaveManagementException extends WebApplicationException {

    public LeaveManagementException(String message, Response.Status status) {
        super(Response.status(status)
                .entity(new ErrorResponse(message, status.getStatusCode()))
                .type("application/json")
                .build());
    }

    public static LeaveManagementException notFound(String message) {
        return new LeaveManagementException(message, Response.Status.NOT_FOUND);
    }

    public static LeaveManagementException badRequest(String message) {
        return new LeaveManagementException(message, Response.Status.BAD_REQUEST);
    }

    public static LeaveManagementException conflict(String message) {
        return new LeaveManagementException(message, Response.Status.CONFLICT);
    }

    public static LeaveManagementException forbidden(String message) {
        return new LeaveManagementException(message, Response.Status.FORBIDDEN);
    }

    public record ErrorResponse(String message, int status) {}
}
