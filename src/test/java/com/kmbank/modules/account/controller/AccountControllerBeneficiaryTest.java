package com.kmbank.modules.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmbank.modules.account.dto.request.SaveBeneficiaryRequest;
import com.kmbank.modules.account.dto.response.BeneficiaryDto;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.account.service.BeneficiaryService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(com.kmbank.config.SecurityConfig.class)
class AccountControllerBeneficiaryTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountService accountService;
    @MockBean BeneficiaryService beneficiaryService;
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

        org.mockito.Mockito.doAnswer(inv -> {
            inv.<jakarta.servlet.FilterChain>getArgument(2)
               .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
               .sendError(401, "Unauthorized"); return null;
        }).when(authenticationEntryPoint).commence(any(), any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
               .sendError(403, "Forbidden"); return null;
        }).when(accessDeniedHandler).handle(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/accounts/beneficiaries - 200 saves beneficiary")
    void saveBeneficiary_success() throws Exception {
        SaveBeneficiaryRequest req = new SaveBeneficiaryRequest("ACC-654321", "Close friend");
        BeneficiaryDto dto = new BeneficiaryDto(
                UUID.randomUUID(), "ACC-654321", "NGUYEN ** ANH", "Close friend", Instant.now());

        when(beneficiaryService.saveBeneficiary(eq(principal.getId()), any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/v1/accounts/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNumber").value("ACC-654321"))
                .andExpect(jsonPath("$.data.displayName").value("Close friend"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/beneficiaries - 200 lists beneficiaries")
    void getBeneficiaries_success() throws Exception {
        BeneficiaryDto dto = new BeneficiaryDto(
                UUID.randomUUID(), "ACC-654321", "NGUYEN ** ANH", "Close friend", Instant.now());

        when(beneficiaryService.getBeneficiaries(eq(principal.getId())))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/accounts/beneficiaries").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].accountNumber").value("ACC-654321"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/beneficiaries - 401 without auth")
    void getBeneficiaries_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/beneficiaries"))
                .andExpect(status().isUnauthorized());
    }
}