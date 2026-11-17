package io.testopsai.server.platform.error;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
    })
@AutoConfigureMockMvc
class ErrorContractTest {
  @Autowired MockMvc mvc;

  @Test
  void healthEndpointRemainsPublicAndUnaffectedByErrorContract() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void unknownPathReturns401WithRequestIdHeaderUnderCurrentSecurityPolicy() throws Exception {
    // Current M1.1 security denies all non-health paths; 404 body unification is deferred to M1.2.
    mvc.perform(get("/non-existent-path-for-test"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }

  @Test
  void unauthorizedBusinessPathReturns401WithRequestIdHeader() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }
}
