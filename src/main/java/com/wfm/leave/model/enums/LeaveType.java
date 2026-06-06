package com.wfm.leave.model.enums;

/**
 * Types of leave supported by the platform.
 * Extensible — new types can be added without schema changes.
 */
public enum LeaveType {
    VACATION,          // Accrues monthly/yearly
    SICK,              // Granted yearly upfront
    PARENTAL,          // One-time bucket
    TENURE_BONUS       // Activated after N years of service
}
