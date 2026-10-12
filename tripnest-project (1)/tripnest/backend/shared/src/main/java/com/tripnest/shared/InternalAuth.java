package com.tripnest.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InternalAuth {
  private InternalAuth() {}

  public static void requireServiceToken(String presented) {
    String expected = System.getenv("SERVICE_SECRET");
    if (expected == null || expected.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("SERVICE_SECRET must be set to at least 32 bytes");
    }
    if (presented == null || !MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid service credential required");
    }
  }
}
