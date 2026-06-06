package com.wfm.leave.dto;

import com.wfm.leave.model.enums.LeaveType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequestDTO {

    @NotNull
    private LeaveType leaveType;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String reason;
}
