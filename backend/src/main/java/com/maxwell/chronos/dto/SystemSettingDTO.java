package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettingDTO {
    private String key;
    private String value;
    private LocalDateTime updatedAt;
}
