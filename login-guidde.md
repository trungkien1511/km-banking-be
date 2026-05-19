🗺️ Lộ trình 7 bước học luồng Login ở Backend:
Bước 1: Tiếp nhận và Xác thực đầu vào (Controller & DTO Validation)
File cần học: AuthController.java & LoginRequest.java
Nội dung:
Tìm hiểu cách Spring MVC hứng dữ liệu qua @PostMapping("/login").
Học cách dùng thư viện jakarta.validation (@NotBlank, @Size, @Valid) để lọc dữ liệu rác (Ví dụ: chặn mật khẩu ngắn hơn 8 ký tự hoặc bắt buộc nhập username) ngay tại "cửa ngõ" Controller trước khi cho chạy vào sâu hơn.
Bước 2: Chuẩn hóa dữ liệu & Ủy quyền xác thực (Service Layer)
File cần học: AuthService.java
Nội dung:
Học cách chuẩn hóa nghiệp vụ (ở đây là trim() khoảng trắng của tài khoản).
Tìm hiểu cách AuthService chuyển giao (delegate) nhiệm vụ xác thực mật khẩu cho bộ máy của Spring Security thông qua lớp AuthenticationManager.
Từ khóa học thêm: UsernamePasswordAuthenticationToken.
Bước 3: Tìm kiếm User trong Database (UserDetailsService)
File cần học: CustomUserDetailsService.java & UserRepository.java
Nội dung:
Tìm hiểu cách Spring Security tự động gọi hàm loadUserByUsername(identifier) của bạn để tìm user trong DB (bằng username hoặc số điện thoại).
Học cách ném ra ngoại lệ chuẩn UsernameNotFoundException khi không tìm thấy tài khoản.
Bước 4: Đóng gói quyền hạn (Principal & UserDetails)
File cần học: CustomUserPrincipal.java & User.java
Nội dung:
Spring Security không làm việc trực tiếp với Class @Entity User của DB. Nó cần một lớp bọc (wrapper) trung gian triển khai interface UserDetails.
Học cách chuyển thông tin tài khoản, mật khẩu đã hash, danh sách quyền (GrantedAuthority), cùng các trạng thái tài khoản (ACTIVE, LOCKED, DISABLED) sang cho Spring Security hiểu.
Bước 5: So khớp mật khẩu đã băm (Password Encoder)
File cần học: SecurityBeansConfig.java (hàm passwordEncoder())
Nội dung:
Học cách Spring Security tự động lấy mật khẩu người dùng gõ vào, băm (hash) bằng thuật toán BCryptPasswordEncoder và đối chiếu với mật khẩu đã lưu trong DB.
Nếu khớp -> Chuyển sang Bước 6.
Nếu lệch -> Ném ra ngoại lệ BadCredentialsException.
Bước 6: Xử lý Tác vụ Phụ sau khi thành công (Spring Events)
File cần học: AuthenticationEventsListener.java
Nội dung:
Đây là phần nâng cao (Event-Driven). Sau khi khớp mật khẩu, Spring Security tự động phát ra một sự kiện AuthenticationSuccessEvent.
Học cách viết @EventListener để "lắng nghe" sự kiện này và chạy bất đồng bộ/hoặc giao dịch độc lập để: Cập nhật thời gian đăng nhập gần nhất (lastLoginAt), và reset số lần đăng nhập sai (failedLoginAttempts = 0).
Bước 7: Tạo Token & Trả về kết quả (JWT & DTO Mapping)
File cần học: JwtService.java, AuthMapper.java, và LoginResponse.java
Nội dung:
Khi xác thực thành công mỹ mãn, AuthService sẽ lấy thông tin User từ cơ sở dữ liệu.
Dùng JwtService để tạo ra chuỗi JWT chứa: userId, username, role.
Dùng AuthMapper để chuyển Entity User thành AuthUserResponse (ẩn đi mật khẩu băm và thông tin nhạy cảm) và trả về JSON chuẩn cho Frontend.
🛡️ Bonus: Học cách hệ thống tự động xử lý khi có lỗi (Exception Handling)
File cần học: GlobalExceptionHandler.java
Nội dung:
Học cách các lỗi ở Bước 3 (Không tìm thấy), Bước 5 (Sai mật khẩu), hoặc khi tài khoản bị khóa (LockedException), bị vô hiệu hóa (DisabledException) được gom lại một nơi.
Tìm hiểu cách trả về thông điệp lỗi ẩn danh (như "Invalid username or password") để hacker không dò tìm được lỗ hổng.