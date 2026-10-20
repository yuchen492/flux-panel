package com.admin.service;

import com.admin.common.dto.SpeedLimitDto;
import com.admin.common.dto.SpeedLimitUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.SpeedLimit;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 限速规则服务类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-04
 */
public interface SpeedLimitService extends IService<SpeedLimit> {

    /**
     * 创建限速规则
     * @param speedLimitDto 限速规则数据
     * @return 结果
     */
    R createSpeedLimit(SpeedLimitDto speedLimitDto);

    /**
     * 获取所有限速规则
     * @return 结果
     */
    R getAllSpeedLimits();

    /**
     * 更新限速规则
     * @param speedLimitUpdateDto 更新数据
     * @return 结果
     */
    R updateSpeedLimit(SpeedLimitUpdateDto speedLimitUpdateDto);

    /**
     * 删除限速规则
     * @param id 限速规则ID
     * @return 结果
     */
    R deleteSpeedLimit(Long id);
}
