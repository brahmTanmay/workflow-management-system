package com.wfm.leave.dto;

import com.wfm.leave.model.enums.LeaveType;
import com.wfm.leave.model.enums.PoolType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalanceDTO {
    private LeaveType leaveType;
    private PoolType poolType;
    private BigDecimal credited;
    private BigDecimal used;
    private BigDecimal held;
    private BigDecimal available;
    private LocalDate cycleStart;
    private LocalDate cycleEnd;
    private LocalDate expiryDate;
}
