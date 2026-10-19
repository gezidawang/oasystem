package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
