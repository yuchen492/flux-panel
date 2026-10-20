package com.admin.service;

import com.admin.common.dto.UserTunnelDto;
import com.admin.common.dto.UserTunnelQueryDto;
import com.admin.common.dto.UserTunnelUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.UserTunnel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户隧道权限服务类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface UserTunnelService extends IService<UserTunnel> {

    /**
     * 分配用户隧道权限
     * @param userTunnelDto 用户隧道权限数据
     * @return 结果
     */
    R assignUserTunnel(UserTunnelDto userTunnelDto);
    
    /**
     * 查询用户隧道权限列表
     * @param queryDto 查询条件
     * @return 结果
     */
    R getUserTunnelList(UserTunnelQueryDto queryDto);
    
    /**
     * 删除用户隧道权限
     * @param id ID
     * @return 结果
     */
    R removeUserTunnel(Integer id);

    
    /**
     * 更新用户隧道权限（包含流量、流量重置时间、到期时间）
     * @param updateDto 更新数据
     * @return 结果
     */
    R updateUserTunnel(UserTunnelUpdateDto updateDto);

}
