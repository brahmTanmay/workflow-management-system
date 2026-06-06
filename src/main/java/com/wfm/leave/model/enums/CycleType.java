package com.wfm.leave.model.enums;

/**
 * Defines how the leave cycle boundary is determined for an employee.
 */
public enum CycleType {
    CALENDAR_YEAR,      // Standard Jan 1 – Dec 31
    EMPLOYEE_START_DATE, // Anniversary-based (hire date)
    CUSTOM               // Custom N-month cycle from a configured start month
}
