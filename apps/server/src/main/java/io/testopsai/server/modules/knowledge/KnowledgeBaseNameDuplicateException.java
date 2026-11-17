package io.testopsai.server.modules.knowledge;

public class KnowledgeBaseNameDuplicateException extends RuntimeException {
  public KnowledgeBaseNameDuplicateException(Long ownerId, String name) {
    super("Knowledge base name already exists for owner " + ownerId + ": " + name);
  }
}
