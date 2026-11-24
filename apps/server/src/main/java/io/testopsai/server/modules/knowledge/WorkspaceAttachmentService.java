package io.testopsai.server.modules.knowledge;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkspaceAttachmentService {
  private static final long MIB = 1024 * 1024;
  private static final Set<String> DOCUMENTS = Set.of("md", "markdown", "pdf", "docx");
  private final Path root;
  private final JdbcTemplate jdbc;
  private final DurableCommandReceipt receipts;
  private final WorkspaceDocumentAccess access;

  WorkspaceAttachmentService(
      @Value("${testopsai.workspace.object-root}") String directory,
      JdbcTemplate jdbc,
      DurableCommandReceipt receipts,
      WorkspaceDocumentAccess access) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
    this.jdbc = jdbc;
    this.receipts = receipts;
    this.access = access;
  }

  @Transactional
  public Attachment upload(AppUser actor, long project, MultipartFile file, String command) {
    access.member(actor, project);
    String name = safeName(file.getOriginalFilename());
    long size = file.getSize();
    validateUpload(name, size);
    String sha = sha256(file);
    return receipts.execute(
        actor.id(),
        "uploadPrivateFile",
        project,
        command,
        new UploadCommand(name, sha, size),
        Attachment.class,
        () -> {
          UUID id = UUID.randomUUID();
          writeObject(id, file);
          jdbc.update(
              """
          INSERT INTO workspace_attachment (id,project_id,uploaded_by,file_name,sha256,size_bytes,expires_at)
          VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6)+INTERVAL 24 HOUR)
          """,
              id.toString(),
              project,
              actor.id(),
              name,
              sha,
              size);
          return attachment(id).dto();
        });
  }

  StoredAttachment claimDocument(AppUser actor, long project, UUID id) {
    var entry = claimPrivate(actor, project, id);
    if (!DOCUMENTS.contains(suffix(entry.fileName()))) {
      throw new WorkspaceFailure(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_FORMAT",
          "Only PDF, DOCX or Markdown are accepted as project documents");
    }
    return entry;
  }

  public StoredAttachment claimPrivate(AppUser actor, long project, UUID id) {
    if (id == null) throw WorkspaceFailure.notFound();
    return jdbc
        .query(
            """
        SELECT id,file_name,sha256,size_bytes,expires_at FROM workspace_attachment
        WHERE id=? AND project_id=? AND uploaded_by=? AND expires_at>UTC_TIMESTAMP(6) FOR UPDATE
        """,
            (rs, i) ->
                new StoredAttachment(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getTimestamp(5).toInstant()),
            id.toString(),
            project,
            actor.id())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  StoredAttachment attachment(UUID id) {
    return jdbc
        .query(
            "SELECT id,file_name,sha256,size_bytes,expires_at FROM workspace_attachment WHERE id=?",
            (rs, i) ->
                new StoredAttachment(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getTimestamp(5).toInstant()),
            id.toString())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  public Resource object(UUID id) {
    var resource = new FileSystemResource(path(id));
    if (!resource.isReadable()) throw WorkspaceFailure.notFound();
    return resource;
  }

  private void writeObject(UUID id, MultipartFile file) {
    Path destination = path(id);
    Path staging = null;
    try {
      Files.createDirectories(root);
      staging = Files.createTempFile(root, ".upload-", ".part");
      try (var stream = file.getInputStream()) {
        Files.copy(stream, staging, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) remove(destination);
            }
          });
    } catch (IOException ex) {
      if (staging != null) remove(staging);
      throw new WorkspaceFailure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OBJECT_STORE_UNAVAILABLE",
          "Document storage is unavailable");
    }
  }

  private static void remove(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      org.slf4j.LoggerFactory.getLogger(WorkspaceAttachmentService.class)
          .warn("Workspace object cleanup deferred");
    }
  }

  private Path path(UUID id) {
    return root.resolve(id.toString());
  }

  private static String safeName(String value) {
    if (value == null
        || value.isBlank()
        || value.codePointCount(0, value.length()) > 160
        || value.indexOf('/') >= 0
        || value.indexOf('\\') >= 0
        || value.indexOf(':') >= 0
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Invalid file name");
    }
    return value;
  }

  private static String suffix(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot <= 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static void validateUpload(String name, long size) {
    long limit =
        switch (suffix(name)) {
          case "md", "markdown", "robot", "resource" -> 2 * MIB;
          case "txt", "log" -> 5 * MIB;
          case "pdf", "docx" -> 20 * MIB;
          case "xml" -> 50 * MIB;
          default ->
              throw new WorkspaceFailure(
                  HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                  "UNSUPPORTED_FORMAT",
                  "File format is not supported");
        };
    if (size == 0) throw new IllegalArgumentException("File is empty");
    if (size > limit)
      throw new WorkspaceFailure(
          HttpStatus.PAYLOAD_TOO_LARGE, "CONTENT_TOO_LARGE", "File exceeds the allowed size");
  }

  private static String sha256(MultipartFile file) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var stream = new DigestInputStream(file.getInputStream(), digest)) {
        stream.transferTo(OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException ex) {
      throw new WorkspaceFailure(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OBJECT_STORE_UNAVAILABLE",
          "Could not read uploaded file");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  record Attachment(String attachmentId, String fileName, String sha256, Instant expiresAt) {}

  public record StoredAttachment(
      String attachmentId, String fileName, String sha256, long sizeBytes, Instant expiresAt) {
    Attachment dto() {
      return new Attachment(attachmentId, fileName, sha256, expiresAt);
    }
  }

  private record UploadCommand(String fileName, String sha256, long sizeBytes) {}
}
