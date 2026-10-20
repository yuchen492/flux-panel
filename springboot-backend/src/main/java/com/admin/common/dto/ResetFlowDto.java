package com.admin.common.dto;


import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ResetFlowDto {

    @NotNull(message = "重置账号id不能为空")
    private Integer id;

    @NotNull(message = "重置类型不能为空")
    private Integer type;
}
