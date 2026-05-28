package com.kmbank.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "username", "fullName", "phoneNumber", "email", "role", "status" })
public class AuthUserResponse {

    private UUID id;

    private String username;

    private String phoneNumber;

    private String fullName;

    private String email;

    private String role;

    private String status;
}
