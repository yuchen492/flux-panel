package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UserTunnelQueryDto {

    @NotNull
    private Integer userId;

} 