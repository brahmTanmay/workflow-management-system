package com.wfm.leave.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Manager dashboard: one entry per direct report with their balance summaries.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamBalanceDTO {
    private Long employeeId;
    private String employeeName;
    private List<BalanceSummaryDTO> balances;
}
