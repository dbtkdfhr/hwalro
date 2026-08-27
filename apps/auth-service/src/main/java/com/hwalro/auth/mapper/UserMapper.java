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

    /**
     * 활성 일반 직원만 반환한다. {@code userIds}가 비어 있으면 전체 목록, 값이 있으면 그중 조건을 만족하는
     * 사용자만 반환한다(존재하지 않거나 비활성이거나 역할이 다르면 결과에서 빠진다).
     */
    List<User> findActiveEmployees(@Param("userIds") List<Long> userIds);
}
