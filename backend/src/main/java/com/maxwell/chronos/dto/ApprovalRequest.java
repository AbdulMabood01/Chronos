package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {
    private Long id;
    private String approvalAction;  // APPROVE or REJECT
    private String rejectionReason;
}
