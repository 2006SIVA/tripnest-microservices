package com.tripnest.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
  private final JdbcTemplate db;
  private final PasswordEncoder passwordEncoder;

  public AdminSeeder(JdbcTemplate db, PasswordEncoder passwordEncoder) {
    this.db = db;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    String email = System.getenv("ADMIN_EMAIL");
    if (email == null || email.isBlank()) throw new IllegalStateException("ADMIN_EMAIL must be set before starting user-service");
    email = email.trim().toLowerCase(java.util.Locale.ROOT);
    String password = System.getenv("ADMIN_PASSWORD");
    if (password == null || password.length() < 12) {
      throw new IllegalStateException("ADMIN_PASSWORD must be set to at least 12 characters before starting user-service");
    }
    Integer count = db.queryForObject("select count(*) from users where email=?", Integer.class, email);
    if (count == null || count == 0) {
      String name = System.getenv().getOrDefault("ADMIN_NAME", "TripNest Admin");
      db.update("insert into users(email,password_hash,name,role) values(?,?,?,'ADMIN')",
          email, passwordEncoder.encode(password), name);
      log.info("Seeded administrator account {} (password not logged)", email);
    }
  }
}
