package com.kmbank.modules.account.controller;

import com.kmbank.modules.account.dto.response.RecentRecipientDto;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.account.service.RecentRecipientService;
import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.enums.UserRole;
import com.kmbank.modules.user.enums.UserStatus;
import com.kmbank.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(com.kmbank.config.SecurityConfig.class)
class AccountControllerRecentRecipientsTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountService accountService;
    @MockBean RecentRecipientService recentRecipientService;
    @MockBean com.kmbank.security.filter.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.kmbank.security.handler.AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean com.kmbank.security.handler.AccessDeniedHandlerImpl accessDeniedHandler;
    @MockBean org.springframework.security.authentication.AuthenticationProvider authenticationProvider;
    @MockBean org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private CustomUserPrincipal principal;

    @BeforeEach
    void setUp() throws Exception {
        User user = User.builder()
                .username("testuser").phoneNumber("0901234567")
                .passwordHash("x").fullName("Test User")
                .role(UserRole.CUSTOMER).status(UserStatus.ACTIVE).build();
        principal = new CustomUserPrincipal(user, UUID.randomUUID());

        doAnswer(inv -> { inv.<jakarta.servlet.FilterChain>getArgument(2)
            .doFilter(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(inv -> { inv.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
            .sendError(401, "Unauthorized"); return null; })
            .when(authenticationEntryPoint).commence(any(), any(), any());
        doAnswer(inv -> { inv.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
            .sendError(403, "Forbidden"); return null; })
            .when(accessDeniedHandler).handle(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/recent-recipients - 200 OK returns list")
    void getRecentRecipients_success() throws Exception {
        RecentRecipientDto dto = new RecentRecipientDto(
                "ACC-123456", "NGUYEN ** ANH", Instant.parse("2026-09-10T10:00:00Z"));

        when(recentRecipientService.getRecentRecipients(eq(principal.getId()), eq(5)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/accounts/recent-recipients")
                        .param("limit", "5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].accountNumber").value("ACC-123456"))
                .andExpect(jsonPath("$.data[0].accountHolderName").value("NGUYEN ** ANH"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/recent-recipients - 401 without auth")
    void getRecentRecipients_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/recent-recipients"))
                .andExpect(status().isUnauthorized());
    }
}
