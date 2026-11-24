package io.testopsai.server.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Pre-hash new passwords before BCrypt so its byte limit cannot truncate Unicode inputs. */
final class AccountPasswordEncoder implements PasswordEncoder {
  private static final String PREFIX = "{bcrypt-sha256}";
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  @Override
  public String encode(CharSequence raw) {
    return PREFIX + bcrypt.encode(prehash(raw));
  }

  @Override
  public boolean matches(CharSequence raw, String encoded) {
    return encoded.startsWith(PREFIX)
        ? bcrypt.matches(prehash(raw), encoded.substring(PREFIX.length()))
        : bcrypt.matches(raw, encoded);
  }

  private static String prehash(CharSequence raw) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update("TestOps AI password\0".getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
