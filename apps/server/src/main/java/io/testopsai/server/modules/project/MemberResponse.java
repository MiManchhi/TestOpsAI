package io.testopsai.server.modules.project;

public record MemberResponse(String userId, String username, String role) {
  public static MemberResponse from(ProjectMember m, String username) {
    return new MemberResponse(String.valueOf(m.userId()), username, m.role());
  }
}
