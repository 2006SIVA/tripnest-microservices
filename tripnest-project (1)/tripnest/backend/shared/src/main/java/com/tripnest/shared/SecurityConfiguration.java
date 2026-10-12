package com.tripnest.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
  public SecurityConfiguration() {
    requireStrongSecret("JWT_SECRET");
    requireStrongSecret("SERVICE_SECRET");
  }

  private static void requireStrongSecret(String name) {
    String secret=System.getenv(name);
    if(secret==null||secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)
      throw new IllegalStateException(name+" must be set to at least 32 bytes before service startup");
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/health", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
            .requestMatchers("/api/v1/bookings/internal/**", "/api/v1/hotels/internal/**", "/api/v1/transport/internal/**").permitAll()
            .requestMatchers("/api/v1/bookings/admin", "/api/v1/bookings/admin/**", "/api/v1/users/admin", "/api/v1/users/admin/**", "/api/v1/auth/admin", "/api/v1/auth/admin/**", "/api/v1/payments/admin", "/api/v1/payments/admin/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/destinations", "/api/v1/destinations/**", "/api/v1/packages", "/api/v1/packages/**", "/api/v1/hotels", "/api/v1/hotels/**", "/api/v1/transport", "/api/v1/transport/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/destinations/**", "/api/v1/packages/**", "/api/v1/hotels/**", "/api/v1/transport/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/destinations/**", "/api/v1/packages/**", "/api/v1/hotels/**", "/api/v1/transport/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/destinations/**", "/api/v1/packages/**", "/api/v1/hotels/**", "/api/v1/transport/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/destinations/**", "/api/v1/packages/**", "/api/v1/hotels/**", "/api/v1/transport/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().permitAll())
        .addFilterBefore(new JwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
