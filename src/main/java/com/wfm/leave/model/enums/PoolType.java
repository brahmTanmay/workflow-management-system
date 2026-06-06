package com.wfm.leave.model.enums;

/**
 * The type of pool a leave balance entry belongs to.
 */
public enum PoolType {
    CURRENT_CYCLE,     // Days accrued/granted in the current leave cycle
    CARRY_FORWARD      // Days carried over from the previous cycle (may have expiry)
}
