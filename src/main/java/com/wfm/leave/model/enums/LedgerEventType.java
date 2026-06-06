package com.wfm.leave.model.enums;

/**
 * Every mutation to a leave balance is recorded as a ledger event of one of these types.
 * This makes the entire balance fully reconstructable from the event stream.
 */
public enum LedgerEventType {
    ACCRUAL,               // Monthly/yearly accrual credited
    GRANT,                 // One-time grant (parental, tenure bonus)
    HOLD,                  // Days reserved when leave request is submitted
    HOLD_RELEASE,          // Held days released (reject/cancel)
    DEDUCTION,             // Days deducted on approval
    CARRY_FORWARD_IN,      // Days rolled into CF pool at cycle boundary
    CARRY_FORWARD_EXPIRY,  // CF pool expired
    POLICY_ADJUSTMENT,     // Retroactive policy correction delta
    MANUAL_ADJUSTMENT      // Admin override
}
