package io.testopsai.server.modules.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceServiceIdentity {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(WorkspaceServiceIdentity.class);
  private final ObjectMapper json;
  private final RSAPrivateCrtKey javaPrivate;
  private final RSAPublicKey javaPublic;
  private final RSAPublicKey aiPublic;

  WorkspaceServiceIdentity(
      ObjectMapper json,
      @Value("${testopsai.workspace.java-private-key}") String javaKey,
      @Value("${testopsai.workspace.ai-public-key}") String aiKey) {
    this.json = json;
    try {
      var factory = KeyFactory.getInstance("RSA");
      javaPrivate =
          javaKey.isBlank()
              ? null
              : (RSAPrivateCrtKey)
                  factory.generatePrivate(new PKCS8EncodedKeySpec(pem(javaKey, "PRIVATE KEY")));
      javaPublic =
          javaPrivate == null
              ? null
              : requiredKey(
                  (RSAPublicKey)
                      factory.generatePublic(
                          new RSAPublicKeySpec(
                              javaPrivate.getModulus(), javaPrivate.getPublicExponent())));
      aiPublic =
          aiKey.isBlank()
              ? null
              : requiredKey(
                  (RSAPublicKey)
                      factory.generatePublic(new X509EncodedKeySpec(pem(aiKey, "PUBLIC KEY"))));
    } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
      throw new IllegalArgumentException(
          "Invalid workspace RS256 signing or verification material");
    }
  }

  public String serviceToken(String operationId) {
    return signed(base("testops-ai", operationId, 60));
  }

  public String answerDelegation(
      String turn, long project, long actor, long revision, String digest) {
    var claims = base("testops-server-tools", turn, 120);
    claims.put("projectId", Long.toString(project));
    claims.put("actorId", Long.toString(actor));
    claims.put("authorizationVersion", revision);
    claims.put("allowActions", List.of("composeAnswer", "resolveEvidence"));
    claims.put("scopeDigest", digest);
    return signed(claims);
  }

  public String runOutputDelegation(
      String job, long project, long actor, long revision, long run, String attachment) {
    return privateObjectDelegation(
        job, project, actor, revision, List.of(Long.toString(run)), List.of(attachment));
  }

  public String answerObjectDelegation(
      String turn, long project, long actor, long revision, List<String> attachments) {
    return privateObjectDelegation(turn, project, actor, revision, List.of(), attachments);
  }

  private String privateObjectDelegation(
      String turn,
      long project,
      long actor,
      long revision,
      List<String> runs,
      List<String> attachments) {
    String projectId = Long.toString(project);
    String actorId = Long.toString(actor);
    var claims = base("testops-server-tools", turn, 120);
    claims.put("projectId", projectId);
    claims.put("actorId", actorId);
    claims.put("authorizationVersion", revision);
    claims.put("objectIds", attachments);
    claims.put(
        "scope",
        Map.of(
            "projectId",
            projectId,
            "actorId",
            actorId,
            "knowledgeBaseIds",
            List.of(projectId),
            "documentIds",
            List.of(),
            "caseIds",
            List.of(),
            "runIds",
            runs,
            "allowedTools",
            List.of(),
            "caseScope",
            "PROJECT",
            "expiresAt",
            Instant.now().plusSeconds(120).toString()));
    return signed(claims);
  }

  String indexDelegation(
      String operationId,
      long project,
      long reviewer,
      long version,
      long authRevision,
      String attachment) {
    var claims = base("testops-server-tools", operationId, 120);
    claims.put("projectId", Long.toString(project));
    claims.put("actorId", Long.toString(reviewer));
    claims.put("authorizationVersion", authRevision);
    claims.put("objectIds", List.of(attachment));
    claims.put(
        "scope",
        Map.of(
            "projectId",
            Long.toString(project),
            "actorId",
            Long.toString(reviewer),
            "knowledgeBaseIds",
            List.of(Long.toString(project)),
            "documentIds",
            List.of(Long.toString(version)),
            "caseIds",
            List.of(),
            "runIds",
            List.of(),
            "allowedTools",
            List.of(),
            "expiresAt",
            Instant.now().plusSeconds(120).toString(),
            "caseScope",
            "PROJECT"));
    return signed(claims);
  }

  String exportDelegation(
      String jobId, long project, long reviewer, long version, long authRevision, String hash) {
    var claims = base("testops-server-tools", jobId, 120);
    claims.put("projectId", Long.toString(project));
    claims.put("actorId", Long.toString(reviewer));
    claims.put("authorizationVersion", authRevision);
    claims.put("allowActions", List.of("exportIndexBlocks"));
    claims.put("versionId", Long.toString(version));
    claims.put("contentHash", hash);
    return signed(claims);
  }

  public Claims readDelegation(String authorization, String delegation) {
    if (!authorization.startsWith("Bearer ")) throw denied();
    Claims service = verify(authorization.substring(7), aiPublic, "testops-ai", 300);
    Claims grant = verify(delegation, javaPublic, "testops-server", 120);
    if (!service.get("operationId", String.class).equals(grant.get("operationId", String.class)))
      throw denied();
    return grant;
  }

  private static Claims verify(
      String token, RSAPublicKey key, String issuer, long maximumLifetime) {
    if (key == null || token.isBlank() || token.length() > 12000) throw denied();
    try {
      var signed =
          Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token);
      if (!"RS256".equals(signed.getHeader().getAlgorithm())) throw denied();
      Claims jwt = signed.getPayload();
      long now = Instant.now().getEpochSecond();
      long issued = jwt.getIssuedAt().toInstant().getEpochSecond();
      long expires = jwt.getExpiration().toInstant().getEpochSecond();
      if (!Set.of("testops-server-tools").equals(jwt.getAudience())
          || issued > now
          || expires <= now
          || expires - issued > maximumLifetime
          || expires <= issued) throw denied();
      WorkspaceDocumentService.parseUuid(jwt.getId());
      WorkspaceDocumentService.parseUuid(jwt.get("operationId", String.class));
      return jwt;
    } catch (RuntimeException ex) {
      log.debug(
          "Workspace service token rejected for issuer {} ({})",
          issuer,
          ex.getClass().getSimpleName());
      throw denied();
    }
  }

  private Map<String, Object> base(String audience, String operation, int lifetime) {
    long now = Instant.now().getEpochSecond();
    var claims = new LinkedHashMap<String, Object>();
    claims.put("iss", "testops-server");
    claims.put("aud", audience);
    claims.put("iat", now);
    claims.put("exp", now + lifetime);
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("operationId", operation);
    return claims;
  }

  private String signed(Map<String, Object> claims) {
    if (javaPrivate == null || aiPublic == null) {
      throw new WorkspaceFailure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "INDEX_NOT_CONFIGURED",
          "Workspace service identity is not configured");
    }
    try {
      var encoder = Base64.getUrlEncoder().withoutPadding();
      String header =
          encoder.encodeToString(
              "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.US_ASCII));
      String value = header + "." + encoder.encodeToString(json.writeValueAsBytes(claims));
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(javaPrivate);
      signer.update(value.getBytes(StandardCharsets.US_ASCII));
      return value + "." + encoder.encodeToString(signer.sign());
    } catch (GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Unable to sign workspace service identity", ex);
    }
  }

  private static byte[] pem(String text, String kind) {
    return Base64.getDecoder()
        .decode(
            text.replace("\\n", "\n")
                .replace("-----BEGIN " + kind + "-----", "")
                .replace("-----END " + kind + "-----", "")
                .replaceAll("\\s", ""));
  }

  private static RSAPublicKey requiredKey(RSAPublicKey key) {
    if (key.getModulus().bitLength() < 2048)
      throw new IllegalArgumentException("Workspace RSA key must be at least 2048 bits");
    return key;
  }

  private static WorkspaceFailure denied() {
    return new WorkspaceFailure(
        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Valid service credentials are required");
  }
}
