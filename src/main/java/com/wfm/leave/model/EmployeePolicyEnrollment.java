package com.wfm.leave.model;

import com.wfm.leave.model.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Links an employee to a policy group.  An employee may be enrolled in
 * multiple policy groups (one per leave type typically).
 */
@Entity
@Table(name = "employee_policy_enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "policyGroupId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeePolicyEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long enrollmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** The policy group this employee is enrolled in */
    @Column(nullable = false)
    private String policyGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    /** The policy version that was active when the employee was enrolled */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private LeavePolicy policy;

    /** Enrollment effective date */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
