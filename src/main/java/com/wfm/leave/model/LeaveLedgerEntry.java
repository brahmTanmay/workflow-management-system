package com.wfm.leave.model;

import com.wfm.leave.model.enums.LedgerEventType;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.model.enums.PoolType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable audit ledger entry.  Every single mutation to an employee's leave
 * balance produces exactly one row here.  The full balance at any point in time
 * can be reconstructed by replaying ledger entries in order.
 *
 * This is the source of truth for auditability.
 */
@Entity
@Table(name = "leave_ledger")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ledgerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoolType poolType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEventType eventType;

    /**
     * Signed delta: positive = credit, negative = debit.
     * e.g. ACCRUAL +2.08, DEDUCTION -3.00, HOLD -5.00, HOLD_RELEASE +5.00
     */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal delta;

    /** Running balance after this event (for quick point-in-time queries) */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal balanceAfter;

    /** The cycle this ledger entry pertains to */
    @Column(nullable = false)
    private LocalDate cycleStart;

    /** FK to the leave request that triggered this event, if any */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id")
    private LeaveRequest leaveRequest;

    /** FK to the policy version in effect when this event occurred */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private LeavePolicy policy;

    /** Free-text note for manual adjustments or policy correction context */
    @Column(length = 1000)
    private String note;

    /** Who/what triggered this event (userId, "SYSTEM", "SCHEDULER", etc.) */
    @Column(nullable = false)
    private String triggeredBy;

    /** Immutable timestamp */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
