package com.kmbank.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Data Transfer Object (DTO) representing the authenticated user's profile.
 * Returned to the client upon successful login or when fetching current user details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// #1: Loại bỏ các trường null khỏi JSON response để tối ưu dung lượng payload
@JsonInclude(JsonInclude.Include.NON_NULL)
// #4: Sắp xếp thứ tự các trường trong JSON cho tường minh và dễ đọc
@JsonPropertyOrder({ "id", "username", "fullName", "phoneNumber", "role" })
public class AuthUserResponse {

    /**
     * ID định danh duy nhất của người dùng (UUID).
     */
    private UUID id;

    /**
     * Tên đăng nhập của người dùng.
     */
    private String username;

    /**
     * Số điện thoại đăng ký của người dùng.
     */
    private String phoneNumber;

    /**
     * Họ và tên đầy đủ của người dùng.
     */
    private String fullName;

    /**
     * Vai trò của người dùng trong hệ thống (VD: USER, ADMIN).
     * Sử dụng String thay vì Enum để linh hoạt hơn trong việc parse JSON ở Frontend.
     */
    private String role;
}
