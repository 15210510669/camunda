package org.camunda.operate.rest;

import org.camunda.operate.security.WebSecurityConfig;
import org.camunda.operate.util.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the health check with enabled authentication.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, HealthCheckRestService.class, WebSecurityConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class HealthCheckRestServiceAuthenticationTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void testHealthStateEndpointIsSecured() {
    final ResponseEntity<String> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

}
