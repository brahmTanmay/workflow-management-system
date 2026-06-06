package com.wfm.leave.model;

import com.wfm.leave.model.enums.LeaveRequestStatus;
import com.wfm.leave.model.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A leave request raised by an employee, going through the approval workflow.
 *
 * Lifecycle:
 *   PENDING  ->  APPROVED  (days moved from held -> used)
 *   PENDING  ->  REJECTED  (held days released)
 *   PENDING  ->  CANCELLED (held days released)
 *   APPROVED ->  CANCELLED (used days credited back)
 */
@Entity
@Table(name = "leave_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Number of leave days requested (may be fractional, e.g. half-days) */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal numberOfDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestStatus status;

    /** The manager who acted on the request */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actioned_by")
    private Employee actionedBy;

    private String reason;

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = LeaveRequestStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
