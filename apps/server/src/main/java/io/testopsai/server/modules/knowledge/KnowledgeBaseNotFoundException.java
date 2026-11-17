package io.testopsai.server.modules.knowledge;

public class KnowledgeBaseNotFoundException extends RuntimeException {
  public KnowledgeBaseNotFoundException(Long id) {
    super("Knowledge base not found: " + id);
  }
}
