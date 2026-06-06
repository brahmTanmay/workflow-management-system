package com.wfm.leave.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveActionDTO {
    private Long actionedById;
    private String reason;
}
