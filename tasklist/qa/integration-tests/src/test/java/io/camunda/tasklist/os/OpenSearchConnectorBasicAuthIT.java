/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestOpenSearchSchemaManager;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestUtil;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestOpenSearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryOpenSearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {OpenSearchConnectorBasicAuthIT.OpenSearchStarter.class})
public class OpenSearchConnectorBasicAuthIT extends TasklistIntegrationTest {

  static OpensearchContainer opensearch =
      (OpensearchContainer)
          new OpensearchContainer("opensearchproject/opensearch:2.9.0")
              .withEnv(
                  Map.of(
                      // "plugins.security.disabled", "false",
                      "OPENSEARCH_PASSWORD", "changeme",
                      "plugins.security.allow_unsafe_democertificates", "true"
                      //        "xpack.security.transport.ssl.enabled","true",
                      //        "xpack.security.http.ssl.enabled", "true",
                      //
                      // "xpack.security.transport.ssl.verification_mode","none",//"certificate",
                      //        "xpack.security.transport.ssl.keystore.path",
                      // "elastic-certificates.p12",
                      //        "xpack.security.transport.ssl.truststore.path",
                      // "elastic-certificates.p12"
                      ))
              .withExposedPorts(9200, 9200);

  @Autowired
  @Qualifier("openSearchClient")
  OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("zeebeOsClient")
  OpenSearchClient zeebeOsClient;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Test
  public void canConnect() {
    assertThat(openSearchClient).isNotNull();
    assertThat(zeebeOsClient).isNotNull();
  }

  static class OpenSearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      opensearch.start();

      final String osUrl =
          String.format("http://%s:%s", opensearch.getHost(), opensearch.getMappedPort(9200));
      TestPropertyValues.of(
              "camunda.tasklist.opensearch.username=opensearch",
              "camunda.tasklist.opensearch.password=changeme",
              "camunda.tasklist.opensearch.url=" + osUrl,
              "camunda.tasklist.opensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.url=" + osUrl,
              "camunda.tasklist.zeebeOpensearch.username=opensearch",
              "camunda.tasklist.zeebeOpensearch.password=changeme",
              "camunda.tasklist.zeebeOpensearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeOpensearch.prefix=zeebe-record")
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
