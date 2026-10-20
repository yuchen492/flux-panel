package com.admin.common.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;

@Data
public class SpeedLimitDto {

    @NotBlank(message = "限速规则名称不能为空")
    private String name;

    @NotNull(message = "速度限制不能为空")
    @Min(value = 1, message = "速度限制必须大于0")
    private Integer speed;

    @NotNull(message = "隧道ID不能为空")
    private Long tunnelId;

    @NotBlank(message = "隧道名称不能为空")
    private String tunnelName;
} 