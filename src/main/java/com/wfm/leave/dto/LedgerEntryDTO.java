package com.wfm.leave.dto;

import com.wfm.leave.model.enums.LedgerEventType;
import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.model.enums.PoolType;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntryDTO {
    private Long ledgerId;
    private LeaveType leaveType;
    private PoolType poolType;
    private LedgerEventType eventType;
    private BigDecimal delta;
    private BigDecimal balanceAfter;
    private LocalDate cycleStart;
    private Long leaveRequestId;
    private String triggeredBy;
    private String note;
    private Instant createdAt;
}
