package com.kmbank.modules.dashboard.controller;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.dashboard.dto.response.DashboardResponse;
import com.kmbank.modules.dashboard.service.DashboardService;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(com.kmbank.config.SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    // Required by SecurityConfig / JwtAuthenticationFilter context
    @MockBean
    private com.kmbank.security.filter.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.kmbank.security.handler.AuthenticationEntryPointImpl authenticationEntryPoint;

    @MockBean
    private com.kmbank.security.handler.AccessDeniedHandlerImpl accessDeniedHandler;

    @MockBean
    private org.springframework.security.authentication.AuthenticationProvider authenticationProvider;

    private UUID testUserId;
    private UUID testCustomerId;
    private CustomUserPrincipal principalWithCustomer;
    private CustomUserPrincipal principalWithoutCustomer;

    @BeforeEach
    void setUp() throws Exception {
        testUserId = UUID.randomUUID();
        testCustomerId = UUID.randomUUID();

        User user = User.builder()
                .username("testuser")
                .phoneNumber("0901234567")
                .passwordHash("hashed_password")
                .fullName("Test User")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        // Set the id via reflection workaround — use BaseEntity's id field
        // We use a builder-friendly approach: just create principal with a known user
        principalWithCustomer = new CustomUserPrincipal(user, testCustomerId);
        principalWithoutCustomer = new CustomUserPrincipal(user, null);

        doAnswer((org.mockito.stubbing.Answer<Object>) invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(
                (jakarta.servlet.http.HttpServletRequest) invocation.getArgument(0),
                (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1)
            );
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer((org.mockito.stubbing.Answer<Object>) invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return null;
        }).when(authenticationEntryPoint).commence(any(), any(), any());

        doAnswer((org.mockito.stubbing.Answer<Object>) invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return null;
        }).when(accessDeniedHandler).handle(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Success case
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/dashboard - 200 OK with dashboard data when user has customer profile")
    void getDashboard_success() throws Exception {
        // Arrange
        AccountResponse account = AccountResponse.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-001")
                .accountType("SAVINGS")
                .status("ACTIVE")
                .balance(new BigDecimal("5000000"))
                .availableBalance(new BigDecimal("5000000"))
                .currency("VND")
                .createdAt(Instant.now())
                .build();

        TransactionResponse txn = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .referenceNumber("REF-001")
                .transactionType("TRANSFER")
                .status("COMPLETED")
                .amount(new BigDecimal("1000000"))
                .currency("VND")
                .direction("OUT")
                .build();

        DashboardResponse dashboardResponse = DashboardResponse.builder()
                .totalBalance(new BigDecimal("5000000"))
                .currency("VND")
                .accounts(List.of(account))
                .recentTransactions(List.of(txn))
                .build();

        when(dashboardService.getDashboard(testCustomerId)).thenReturn(dashboardResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(user(principalWithCustomer)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalBalance").value(5000000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.accounts").isArray())
                .andExpect(jsonPath("$.data.accounts[0].accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.data.accounts[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.recentTransactions").isArray())
                .andExpect(jsonPath("$.data.recentTransactions[0].referenceNumber").value("REF-001"))
                .andExpect(jsonPath("$.data.recentTransactions[0].direction").value("OUT"));
    }

    // -----------------------------------------------------------------------
    // 403 Forbidden — user has no customer profile
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/dashboard - 403 Forbidden when user has no customer profile")
    void getDashboard_noCustomerProfile_returns403() throws Exception {
        // The controller itself throws FORBIDDEN when customerId is null
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(user(principalWithoutCustomer)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // 401 Unauthorized — no authentication token
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/dashboard - 401 Unauthorized when no authentication provided")
    void getDashboard_noAuth_returns401() throws Exception {
        // Without security post-processor, request is unauthenticated
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 500 Internal Server Error — service throws unexpected exception
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/dashboard - 500 when service throws unexpected exception")
    void getDashboard_serviceError_returns500() throws Exception {
        when(dashboardService.getDashboard(any(UUID.class)))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(user(principalWithCustomer)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
