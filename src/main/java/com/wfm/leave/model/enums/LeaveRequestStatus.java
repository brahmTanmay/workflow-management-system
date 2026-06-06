package com.wfm.leave.model.enums;

/**
 * Status of a leave request through the approval workflow.
 */
public enum LeaveRequestStatus {
    PENDING,     // Submitted, days held against balance
    APPROVED,    // Manager approved, days deducted
    REJECTED,    // Manager rejected, held days released
    CANCELLED    // Employee cancelled, held days released
}
