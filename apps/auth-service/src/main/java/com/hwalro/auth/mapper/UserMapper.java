package com.hwalro.auth.mapper;

import com.hwalro.auth.domain.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User findByLoginId(@Param("loginId") String loginId);

    List<User> findByUserIds(@Param("userIds") List<Long> userIds);

    List<String> findRoleNamesByLoginId(@Param("loginId") String loginId);
}
