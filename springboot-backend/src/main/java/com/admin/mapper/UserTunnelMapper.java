package com.admin.mapper;

import com.admin.entity.UserTunnel;
import com.admin.common.dto.UserTunnelWithDetailDto;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface UserTunnelMapper extends BaseMapper<UserTunnel> {


    List<UserTunnelWithDetailDto> getUserTunnelWithDetails(@Param("userId") Integer userId);

}
