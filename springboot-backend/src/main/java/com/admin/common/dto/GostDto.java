package com.admin.common.dto;

import lombok.Data;

@Data
public class GostDto {
    private Integer code;

    private String msg;
    
    private Object data;  // 添加数据字段，用于存储响应的详细数据
}
