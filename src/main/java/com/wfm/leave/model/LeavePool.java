package com.wfm.leave.model;

import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.model.enums.PoolType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single balance pool for one employee, one leave type, one cycle.
 *
 * An employee will typically have up to 2 pools per leave type at any time:
 *   - CURRENT_CYCLE  (this year's accrual/grant)
 *   - CARRY_FORWARD  (leftover from the previous cycle, may expire)
 *
 * Optimistic locking (@Version) prevents concurrent approvals from
 * double-deducting.
 */
@Entity
@Table(name = "leave_pools",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"employee_id", "leaveType", "poolType", "cycleStart"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeavePool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long poolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private LeavePolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoolType poolType;

    /** First day of the leave cycle this pool belongs to */
    @Column(nullable = false)
    private LocalDate cycleStart;

    /** Last day of the leave cycle this pool belongs to */
    @Column(nullable = false)
    private LocalDate cycleEnd;

    /** Total days credited into this pool (accruals + grants + adjustments) */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal credited;

    /** Days consumed (approved leave) */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal used;

    /** Days currently held/reserved by pending leave requests */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal held;

    /** Expiry date for carry-forward pools; null for current-cycle pools */
    private LocalDate expiryDate;

    /** Optimistic lock version for concurrency safety */
    @Version
    private long versionLock;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (credited == null) credited = BigDecimal.ZERO;
        if (used == null) used = BigDecimal.ZERO;
        if (held == null) held = BigDecimal.ZERO;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Available = credited - used - held
     */
    public BigDecimal getAvailable() {
        return credited.subtract(used).subtract(held);
    }
}
