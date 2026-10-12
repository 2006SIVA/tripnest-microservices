package com.tripnest.shared;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || authorization.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    if (!authorization.startsWith("Bearer ")) {
      unauthorized(response);
      return;
    }
    try {
      Claims claims = JwtTokens.verify(authorization.substring(7));
      Long userId = claims.get("userId", Long.class);
      String email = claims.getSubject();
      String role = claims.get("role", String.class);
      if (userId == null || email == null || !("ADMIN".equals(role) || "TRAVELLER".equals(role))) {
        unauthorized(response);
        return;
      }
      var principal = new TripNestPrincipal(userId, email, role);
      var authentication = new UsernamePasswordAuthenticationToken(
          principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      chain.doFilter(request, response);
    } catch (Exception ex) {
      SecurityContextHolder.clearContext();
      unauthorized(response);
    }
  }

  private static void unauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"A valid bearer token is required\"}");
  }
}
