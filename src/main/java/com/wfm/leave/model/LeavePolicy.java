package com.wfm.leave.model;

import com.wfm.leave.model.enums.AccrualMethod;
import com.wfm.leave.model.enums.CycleType;
import com.wfm.leave.model.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versioned leave policy.  Every modification creates a new version row so that
 * retroactive corrections can diff old vs new and recompute balances.
 *
 * The combination (policyGroupId, version) is unique.  policyGroupId ties all
 * versions of the "same" policy together.
 */
@Entity
@Table(name = "leave_policies",
       uniqueConstraints = @UniqueConstraint(columnNames = {"policyGroupId", "version"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    /** Logical identifier shared across versions of the same policy */
    @Column(nullable = false)
    private String policyGroupId;

    /** Monotonically increasing version within a policy group */
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccrualMethod accrualMethod;

    /** Total entitlement per cycle in days (e.g. 25.0) */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal entitlementDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleType cycleType;

    /**
     * For CUSTOM cycle type: the month (1-12) the cycle starts.
     * Ignored for CALENDAR_YEAR and EMPLOYEE_START_DATE.
     */
    private Integer customCycleStartMonth;

    /** Whether unused days carry forward to the next cycle */
    @Column(nullable = false)
    private boolean carryForwardEnabled;

    /** Max days that can be carried forward (null = unlimited when enabled) */
    private BigDecimal carryForwardMaxDays;

    /** How many months the carry-forward pool stays valid (null = no expiry) */
    private Integer carryForwardExpiryMonths;

    /** Minimum tenure in years before this leave type activates (e.g. tenure bonus) */
    @Column(nullable = false)
    private int minTenureYears;

    /** Whether this version is the currently active one for the group */
    @Column(nullable = false)
    private boolean active;

    /** When this version was created */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
