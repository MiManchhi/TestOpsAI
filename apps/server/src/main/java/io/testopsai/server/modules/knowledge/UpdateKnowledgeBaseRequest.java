package io.testopsai.server.modules.knowledge;

record UpdateKnowledgeBaseRequest(
    String name, String description, String visibility, String status) {}
