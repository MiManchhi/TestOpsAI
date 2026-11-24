package io.testopsai.server.modules.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceServiceIdentityTest {
  @Test
  void javaAcceptsAiCallbackOnlyForTheSignedOperation() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var javaKey = generator.generateKeyPair();
    var aiKey = generator.generateKeyPair();
    var identity =
        new WorkspaceServiceIdentity(
            new ObjectMapper(),
            Base64.getEncoder().encodeToString(javaKey.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(aiKey.getPublic().getEncoded()));
    String operation = UUID.randomUUID().toString();
    String attachment = UUID.randomUUID().toString();
    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .issuer("testops-ai")
            .audience()
            .add("testops-server-tools")
            .and()
            .claim("operationId", operation)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(aiKey.getPrivate(), Jwts.SIG.RS256)
            .compact();
    var grant =
        identity.readDelegation(
            "Bearer " + token, identity.indexDelegation(operation, 1, 2, 3, 4, attachment));
    assertThat(grant.get("actorId", String.class)).isEqualTo("2");
    assertThat(grant.get("authorizationVersion", Number.class).longValue()).isEqualTo(4);
    assertThatThrownBy(
            () ->
                identity.readDelegation(
                    "Bearer " + token,
                    identity.indexDelegation(UUID.randomUUID().toString(), 1, 2, 3, 4, attachment)))
        .isInstanceOf(WorkspaceFailure.class);
  }
}
