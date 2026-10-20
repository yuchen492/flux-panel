package com.admin.mapper;

import com.admin.entity.User;
import com.admin.common.dto.UserPackageDto;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 查询用户隧道权限详情
     * @param userId 用户ID
     * @return 隧道权限列表
     */
    List<UserPackageDto.UserTunnelDetailDto> getUserTunnelDetails(@Param("userId") Integer userId);
    
    /**
     * 查询用户转发详情
     * @param userId 用户ID
     * @return 转发列表
     */
    List<UserPackageDto.UserForwardDetailDto> getUserForwardDetails(@Param("userId") Integer userId);
    
    /**
     * 管理员查询所有隧道（流量和转发设置为99999）
     * @return 隧道列表
     */
    List<UserPackageDto.UserTunnelDetailDto> getAllTunnelsForAdmin();
}
