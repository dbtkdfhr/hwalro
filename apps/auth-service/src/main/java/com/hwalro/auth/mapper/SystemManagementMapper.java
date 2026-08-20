package com.hwalro.auth.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemManagementMapper {
    @Select(
            """
            SELECT user_id AS userId,
                   login_id AS loginId,
                   name,
                   enabled,
                   created_at AS createdAt
            FROM users
            ORDER BY user_id
            """)
    List<UserRow> findAllUsers();

    @Select(
            """
            SELECT user_id AS userId,
                   login_id AS loginId,
                   name,
                   enabled,
                   created_at AS createdAt
            FROM users
            WHERE user_id = #{userId}
            """)
    UserRow findUserById(Long userId);

    @Select(
            """
            SELECT ur.user_id AS userId,
                   r.role_name AS roleName
            FROM user_roles ur
            JOIN roles r ON r.role_id = ur.role_id
            ORDER BY ur.user_id, r.role_id
            """)
    List<UserRoleRow> findAllUserRoles();

    @Select(
            """
            SELECT role_id AS roleId,
                   role_name AS roleName,
                   description
            FROM roles
            ORDER BY role_id
            """)
    List<RoleRow> findAllRoles();

    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE login_id = #{loginId})")
    boolean existsByLoginId(String loginId);

    @Insert(
            """
            INSERT INTO users (login_id, password, name, enabled)
            VALUES (#{loginId}, #{password}, #{name}, TRUE)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insertUser(NewUserRow user);

    @Insert(
            """
            <script>
            INSERT INTO user_roles (user_id, role_id)
            VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Update("UPDATE users SET enabled = #{enabled} WHERE user_id = #{userId}")
    int updateUserEnabled(@Param("userId") Long userId, @Param("enabled") boolean enabled);

    record UserRow(Long userId, String loginId, String name, boolean enabled, LocalDateTime createdAt) {}

    record UserRoleRow(Long userId, String roleName) {}

    record RoleRow(Long roleId, String roleName, String description) {}

    class NewUserRow {
        private Long userId;
        private final String loginId;
        private final String password;
        private final String name;

        public NewUserRow(String loginId, String password, String name) {
            this.loginId = loginId;
            this.password = password;
            this.name = name;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getLoginId() {
            return loginId;
        }

        public String getPassword() {
            return password;
        }

        public String getName() {
            return name;
        }
    }
}
