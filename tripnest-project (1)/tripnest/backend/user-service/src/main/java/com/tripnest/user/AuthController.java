package com.tripnest.user;

import com.tripnest.shared.JwtTokens;
import com.tripnest.shared.TripNestPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final JdbcTemplate db;
  private final PasswordEncoder passwordEncoder;

  public AuthController(JdbcTemplate db, PasswordEncoder passwordEncoder) {
    this.db = db;
    this.passwordEncoder = passwordEncoder;
  }

  public record RegisterRequest(@NotBlank @Size(max = 120) String name,
                                @NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @Size(max = 40) String phone) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record ProfileUpdate(@NotBlank @Size(max = 120) String name, @Size(max = 40) String phone) {}
  public record PasswordUpdate(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  @PostMapping("/register")
  public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
    String email = normalize(request.email());
    if (db.queryForObject("select count(*) from users where email=?", Integer.class, email) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
    }
    Long id = db.queryForObject("insert into users(email,password_hash,name,phone,role) values(?,?,?,?,'TRAVELLER') returning id",
        Long.class, email, passwordEncoder.encode(request.password()), request.name().trim(), request.phone());
    return session(id, email, request.name().trim(), "TRAVELLER");
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
    var users = db.queryForList("select id,email,password_hash,name,role from users where email=?", normalize(request.email()));
    if (users.isEmpty() || !passwordEncoder.matches(request.password(), (String) users.getFirst().get("password_hash"))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
    var user = users.getFirst();
    return session(((Number) user.get("id")).longValue(), (String) user.get("email"), (String) user.get("name"), (String) user.get("role"));
  }

  @GetMapping("/profile")
  public Map<String, Object> profile(@AuthenticationPrincipal TripNestPrincipal principal) {
    return db.queryForMap("select id,email,name,phone,role,created_at from users where id=?", principal.userId());
  }

  @PutMapping("/profile")
  public Map<String, Object> updateProfile(@AuthenticationPrincipal TripNestPrincipal principal,
                                           @Valid @RequestBody ProfileUpdate update) {
    db.update("update users set name=?,phone=? where id=?", update.name().trim(), update.phone(), principal.userId());
    return profile(principal);
  }

  @PutMapping("/profile/password")
  public Map<String, String> updatePassword(@AuthenticationPrincipal TripNestPrincipal principal,
                                            @Valid @RequestBody PasswordUpdate update) {
    String currentHash = db.queryForObject("select password_hash from users where id=?", String.class, principal.userId());
    if (!passwordEncoder.matches(update.currentPassword(), currentHash)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }
    db.update("update users set password_hash=? where id=?", passwordEncoder.encode(update.newPassword()), principal.userId());
    return Map.of("status", "PASSWORD_UPDATED");
  }

  @GetMapping("/admin/users")
  public java.util.List<Map<String, Object>> allUsers() {
    return db.queryForList("select id,email,name,phone,role,created_at from users order by created_at desc limit 500");
  }

  private Map<String, Object> session(long id, String email, String name, String role) {
    return Map.of("token", JwtTokens.issue(id, email, role),
        "user", Map.of("id", id, "email", email, "name", name, "role", role));
  }

  private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
