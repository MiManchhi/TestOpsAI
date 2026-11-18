package io.testopsai.server.platform.error;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.testopsai.server.modules.identity.UserRepository;
import io.testopsai.server.modules.knowledge.KnowledgeBaseRepository;
import io.testopsai.server.modules.project.ProjectRepository;
import io.testopsai.server.modules.testcase.TestCaseRepository;
import io.testopsai.server.platform.cache.RedisCacheService;
import io.testopsai.server.platform.idempotency.IdempotencyService;
import io.testopsai.server.platform.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
      "testopsai.jwt.secret=test-secret-key-at-least-32-bytes-long!!"
    })
@AutoConfigureMockMvc
class ErrorContractTest {
  @Autowired MockMvc mvc;
  @MockBean UserRepository userRepository;
  @MockBean KnowledgeBaseRepository knowledgeBaseRepository;
  @MockBean TestCaseRepository testCaseRepository;
  @MockBean RedisCacheService redisCacheService;
  @MockBean RateLimiter rateLimiter;
  @MockBean ProjectRepository projectRepository;
  @MockBean IdempotencyService idempotencyService;

  @Test
  void healthEndpointRemainsPublicAndUnaffectedByErrorContract() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void unknownPathReturnsStructured401WithRequestId() throws Exception {
    mvc.perform(get("/non-existent-path-for-test"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").exists())
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }

  @Test
  void unauthorizedBusinessPathReturnsStructured401() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }
}
