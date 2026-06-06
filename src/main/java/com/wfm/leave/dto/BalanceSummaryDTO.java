package com.wfm.leave.dto;

import com.wfm.leave.model.enums.LeaveType;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated balance summary for a single leave type (across pools).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalanceSummaryDTO {
    private LeaveType leaveType;
    private BigDecimal totalAvailable;
    private BigDecimal totalCredited;
    private BigDecimal totalUsed;
    private BigDecimal totalHeld;
    private List<BalanceDTO> pools;
}
