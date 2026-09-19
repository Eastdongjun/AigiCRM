package com.aigicrm.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Minimal active-position context exposed to PC and mobile clients. */
@Data
@AllArgsConstructor
public class PositionSummary {
    private Long id;
    private String name;
    private String code;
    private Long orgId;
    private Long groupId;
}
