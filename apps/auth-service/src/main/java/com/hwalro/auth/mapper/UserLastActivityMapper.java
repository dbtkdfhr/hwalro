package com.hwalro.auth.mapper;

import com.hwalro.auth.domain.UserLastActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserLastActivityMapper {
    UserLastActivity findByUserId(@Param("userId") Long userId);

    int upsert(
            @Param("userId") Long userId,
            @Param("activityType") String activityType,
            @Param("resourceId") Long resourceId);
}
