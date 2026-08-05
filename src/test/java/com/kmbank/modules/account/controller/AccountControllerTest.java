package com.kmbank.modules.account.controller;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.service.AccountService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(com.kmbank.config.SecurityConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    // SecurityConfig dependencies
    @MockBean
    private com.kmbank.security.filter.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.kmbank.security.handler.AuthenticationEntryPointImpl authenticationEntryPoint;

    @MockBean
    private com.kmbank.security.handler.AccessDeniedHandlerImpl accessDeniedHandler;

    @MockBean
    private org.springframework.security.authentication.AuthenticationProvider authenticationProvider;

    private UUID testUserId;
    private UUID testAccountId;
    private CustomUserPrincipal principal;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testAccountId = UUID.randomUUID();

        User user = User.builder()
                .username("testuser")
                .phoneNumber("0901234567")
                .passwordHash("hashed_password")
                .fullName("Test User")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        principal = new CustomUserPrincipal(user, UUID.randomUUID());
    }

    // -----------------------------------------------------------------------
    // 200 OK — success case
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} - 200 OK with account details")
    void getAccountDetail_success() throws Exception {
        // Arrange
        AccountResponse accountResponse = AccountResponse.builder()
                .id(testAccountId)
                .accountNumber("ACC-123456")
                .accountType("SAVINGS")
                .status("ACTIVE")
                .balance(new BigDecimal("10000000"))
                .availableBalance(new BigDecimal("9500000"))
                .currency("VND")
                .createdAt(Instant.now())
                .build();

        when(accountService.getAccountDetail(eq(principal.getId()), eq(testAccountId)))
                .thenReturn(accountResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{accountId}", testAccountId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(testAccountId.toString()))
                .andExpect(jsonPath("$.data.accountNumber").value("ACC-123456"))
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.balance").value(10000000))
                .andExpect(jsonPath("$.data.availableBalance").value(9500000))
                .andExpect(jsonPath("$.data.currency").value("VND"));
    }

    // -----------------------------------------------------------------------
    // 403 Forbidden — user does not own the account
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} - 403 Forbidden when user does not own account")
    void getAccountDetail_forbidden_userDoesNotOwnAccount() throws Exception {
        when(accountService.getAccountDetail(eq(principal.getId()), eq(testAccountId)))
                .thenThrow(new BusinessException("Access denied to account", ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", testAccountId)
                        .with(user(principal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // -----------------------------------------------------------------------
    // 404 Not Found — account does not exist
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} - 404 Not Found when account does not exist")
    void getAccountDetail_notFound_accountDoesNotExist() throws Exception {
        when(accountService.getAccountDetail(eq(principal.getId()), eq(testAccountId)))
                .thenThrow(new BusinessException("Account not found", ErrorCode.ACCOUNT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", testAccountId)
                        .with(user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // -----------------------------------------------------------------------
    // 401 Unauthorized — no authentication provided
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} - 401 Unauthorized when no authentication provided")
    void getAccountDetail_unauthorized_noAuth() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}", testAccountId))
                .andExpect(status().isUnauthorized());
    }
}
