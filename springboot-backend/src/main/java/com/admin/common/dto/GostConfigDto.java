package com.admin.common.dto;


import lombok.Data;

import java.util.List;

@Data
public class GostConfigDto {

    private List<ConfigItem> limiters;

    private List<ConfigItem> chains;

    private List<ConfigItem> services;


}

