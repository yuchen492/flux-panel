package com.admin.controller;

import com.admin.common.aop.LogAnnotation;
import com.admin.common.annotation.RequireRole;
import com.admin.common.dto.SpeedLimitDto;
import com.admin.common.dto.SpeedLimitUpdateDto;
import com.admin.common.lang.R;
import com.admin.service.SpeedLimitService;
import com.admin.service.TunnelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 限速规则前端控制器
 * </p>
 *
 * @author QAQ
 * @since 2025-06-04
 */
@RestController
@RequestMapping("/api/v1/speed-limit")
@CrossOrigin
public class SpeedLimitController extends BaseController {

    @Autowired
    private SpeedLimitService speedLimitService;

    @Autowired
    private TunnelService tunnelService;

    @LogAnnotation
    @RequireRole
    @PostMapping("/create")
    public R create(@Validated @RequestBody SpeedLimitDto speedLimitDto) {
        return speedLimitService.createSpeedLimit(speedLimitDto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/list")
    public R list() {
        return speedLimitService.getAllSpeedLimits();
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/update")
    public R update(@Validated @RequestBody SpeedLimitUpdateDto speedLimitUpdateDto) {
        return speedLimitService.updateSpeedLimit(speedLimitUpdateDto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/delete")
    public R delete(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        return speedLimitService.deleteSpeedLimit(id);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/tunnels")
    public R getTunnels() {
        return tunnelService.getAllTunnels();
    }
}
