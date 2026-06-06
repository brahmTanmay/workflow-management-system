package com.wfm.leave.model.enums;

/**
 * How leave entitlement is granted to the employee.
 */
public enum AccrualMethod {
    MONTHLY,       // Fraction credited each month (e.g. 25/12 per month)
    YEARLY,        // Full entitlement credited at cycle start
    ONE_TIME       // Single grant, never re-accrues (e.g. parental leave)
}
