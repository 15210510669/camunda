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
package io.camunda.tasklist.qa.util;

import com.github.dockerjava.api.model.Bind;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.RetryOperation;
import io.camunda.tasklist.util.TasklistPropertiesUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.keycloak.admin.client.Keycloak;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@Component
@Configuration
@EnableRetry
public class TestContainerUtil {

  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final String OS_NETWORK_ALIAS = "opensearch";

  public static final String KEYCLOAK_NETWORK_ALIAS = "keycloak";

  public static final String POSTGRES_NETWORK_ALIAS = "postgres";
  public static final String IDENTITY_NETWORK_ALIAS = "identity";
  public static final int ELS_PORT = 9200;
  public static final int OS_PORT = 9200;
  public static final int POSTGRES_PORT = 5432;
  public static final Integer KEYCLOAK_PORT = 8080;
  public static final Integer IDENTITY_PORT = 8080;
  public static final String KEYCLOAK_INIT_OPERATE_SECRET = "the-cake-is-alive";
  public static final String KEYCLOAK_INIT_TASKLIST_SECRET = "the-cake-is-alive";
  public static final String KEYCLOAK_USERNAME = "demo";
  public static final String KEYCLOAK_PASSWORD = "demo";
  public static final String KEYCLOAK_USERNAME_2 = "user2";
  public static final String KEYCLOAK_PASSWORD_2 = "user2";
  public static final String KEYCLOAK_USERS_0_ROLES_0 = "Identity";
  public static final String KEYCLOAK_USERS_0_ROLES_1 = "Tasklist";
  public static final String KEYCLOAK_USERS_0_ROLES_2 = "Operate";
  public static final String IDENTITY_DATABASE_HOST = "postgres";
  public static final String IDENTITY_DATABASE_NAME = "identity";
  public static final String IDENTITY_DATABASE_USERNAME = "identity";
  public static final String IDENTITY_DATABASE_PASSWORD = "t2L@!AqSMg8%I%NmHM";
  public static final String PROPERTIES_PREFIX = "camunda.tasklist.";
  public static final String TENANT_1 = "tenant_1";
  public static final String TENANT_2 = "tenant_2";
  private static final Logger LOGGER = LoggerFactory.getLogger(TestContainerUtil.class);
  private static final String DOCKER_TASKLIST_IMAGE_NAME = "camunda/tasklist";
  private static final Integer TASKLIST_HTTP_PORT = 8080;
  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME =
      "docker.elastic.co/elasticsearch/elasticsearch";
  private static final String DOCKER_OPENSEARCH_IMAGE_NAME = "opensearchproject/opensearch";
  private static final String DOCKER_OPENSEARCH_IMAGE_VERSION = "2.9.0";
  private static final String ZEEBE = "zeebe";
  private static final String KEYCLOAK_ZEEBE_SECRET = "zecret";
  private static final String USER_MEMBER_TYPE = "USER";
  private static final String APPLICATION_MEMBER_TYPE = "APPLICATION";
  private static final String TASKLIST = "tasklist";
  private ElasticsearchContainer elsContainer;
  private OpensearchContainer osContainer;
  private ZeebeContainer broker;
  private GenericContainer tasklistContainer;
  private GenericContainer identityContainer;
  private GenericContainer keycloakContainer;
  private PostgreSQLContainer postgreSQLContainer;

  private Keycloak keycloakClient;

  public void startIdentity(TestContext testContext, String version, boolean multiTenancyEnabled) {
    startPostgres(testContext);
    startKeyCloak(testContext);

    LOGGER.info("************ Starting Identity ************");
    identityContainer =
        new GenericContainer<>(String.format("%s:%s", "camunda/identity", version))
            .withExposedPorts(IDENTITY_PORT)
            .withNetwork(Network.SHARED)
            .withNetworkAliases(IDENTITY_NETWORK_ALIAS);

    identityContainer.withEnv("SERVER_PORT", String.valueOf(IDENTITY_PORT));
    identityContainer.withEnv("KEYCLOAK_URL", testContext.getInternalKeycloakBaseUrl() + "/auth");
    identityContainer.withEnv(
        "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
        String.format(
            "http://%s:%s/auth/realms/camunda-platform",
            testContext.getInternalKeycloakHost(), testContext.getInternalKeycloakPort()));
    identityContainer.withEnv("IDENTITY_DATABASE_HOST", IDENTITY_DATABASE_HOST);
    identityContainer.withEnv("IDENTITY_DATABASE_PORT", String.valueOf(POSTGRES_PORT));
    identityContainer.withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME);
    identityContainer.withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME);
    identityContainer.withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD);
    identityContainer.withEnv("KEYCLOAK_INIT_OPERATE_SECRET", KEYCLOAK_INIT_OPERATE_SECRET);
    identityContainer.withEnv("KEYCLOAK_INIT_OPERATE_ROOT_URL", "http://localhost:8081");
    identityContainer.withEnv("KEYCLOAK_INIT_TASKLIST_SECRET", KEYCLOAK_INIT_TASKLIST_SECRET);
    identityContainer.withEnv("KEYCLOAK_INIT_TASKLIST_ROOT_URL", "http://localhost:8082");
    identityContainer.withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", KEYCLOAK_ZEEBE_SECRET);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_SECRET", KEYCLOAK_ZEEBE_SECRET);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_TYPE", "M2M");
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", "zeebe-api");
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*");
    identityContainer.withEnv("KEYCLOAK_USERS_0_USERNAME", KEYCLOAK_USERNAME);
    identityContainer.withEnv("KEYCLOAK_USERS_0_PASSWORD", KEYCLOAK_PASSWORD);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_0", KEYCLOAK_USERS_0_ROLES_0);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_1", KEYCLOAK_USERS_0_ROLES_1);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_2", KEYCLOAK_USERS_0_ROLES_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_USERNAME", KEYCLOAK_USERNAME_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_PASSWORD", KEYCLOAK_PASSWORD_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_0", KEYCLOAK_USERS_0_ROLES_0);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_1", KEYCLOAK_USERS_0_ROLES_1);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_2", KEYCLOAK_USERS_0_ROLES_2);
    identityContainer.withEnv("RESOURCE_PERMISSIONS_ENABLED", "true");
    identityContainer.withEnv("MULTITENANCY_ENABLED", String.valueOf(multiTenancyEnabled));
    if (multiTenancyEnabled) {
      // tenant1 (members: demo)
      identityContainer.withEnv("IDENTITY_TENANTS_0_NAME", TENANT_1);
      identityContainer.withEnv("IDENTITY_TENANTS_0_TENANT_ID", TENANT_1);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_0_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_0_USERNAME", KEYCLOAK_USERNAME);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_1_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_1_APPLICATION_ID", ZEEBE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_2_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_2_APPLICATION_ID", TASKLIST);
      // tenant2 (members: user2, demo)
      identityContainer.withEnv("IDENTITY_TENANTS_1_NAME", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_TENANT_ID", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_USERNAME", KEYCLOAK_USERNAME_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_APPLICATION_ID", ZEEBE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_APPLICATION_ID", TASKLIST);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_3_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_3_USERNAME", KEYCLOAK_USERNAME);
    }

    identityContainer.start();

    testContext.setExternalIdentityHost(identityContainer.getContainerIpAddress());
    testContext.setExternalIdentityPort(identityContainer.getMappedPort(IDENTITY_PORT));
    testContext.setInternalIdentityHost(IDENTITY_NETWORK_ALIAS);
    testContext.setInternalIdentityPort(IDENTITY_PORT);
    LOGGER.info(
        "************ Identity started on {}:{} ************",
        testContext.getExternalIdentityHost(),
        testContext.getExternalIdentityPort());
  }

  public void startKeyCloak(TestContext testContext) {
    LOGGER.info("************ Starting Keycloak ************");
    keycloakContainer =
        new GenericContainer<>(DockerImageName.parse("bitnami/keycloak:22.0.1"))
            .withExposedPorts(KEYCLOAK_PORT)
            .withEnv(
                Map.of(
                    "KEYCLOAK_HTTP_RELATIVE_PATH",
                    "/auth",
                    "KEYCLOAK_DATABASE_USER",
                    IDENTITY_DATABASE_USERNAME,
                    "KEYCLOAK_DATABASE_PASSWORD",
                    IDENTITY_DATABASE_PASSWORD,
                    "KEYCLOAK_DATABASE_NAME",
                    IDENTITY_DATABASE_NAME,
                    "KEYCLOAK_ADMIN_USER",
                    "admin",
                    "KEYCLOAK_ADMIN_PASSWORD",
                    "admin",
                    "KEYCLOAK_DATABASE_HOST",
                    POSTGRES_NETWORK_ALIAS))
            .dependsOn(postgreSQLContainer)
            .withNetwork(Network.SHARED)
            .withNetworkAliases(KEYCLOAK_NETWORK_ALIAS);
    keycloakContainer.start();
    testContext.setExternalKeycloakHost(keycloakContainer.getContainerIpAddress());
    testContext.setExternalKeycloakPort(keycloakContainer.getFirstMappedPort());
    testContext.setInternalKeycloakHost(KEYCLOAK_NETWORK_ALIAS);
    testContext.setInternalKeycloakPort(KEYCLOAK_PORT);

    LOGGER.info(
        "************ Keycloak started on {}:{} ************",
        testContext.getExternalKeycloakHost(),
        testContext.getExternalKeycloakPort());

    keycloakClient =
        Keycloak.getInstance(
            testContext.getExternalKeycloakBaseUrl() + "/auth",
            "master",
            "admin",
            "admin",
            "admin-cli");
  }

  public void startPostgres(TestContext testContext) {
    LOGGER.info("************ Starting Postgres ************");
    postgreSQLContainer =
        new PostgreSQLContainer("postgres:15.2-alpine")
            .withDatabaseName(IDENTITY_DATABASE_NAME)
            .withUsername(IDENTITY_DATABASE_USERNAME)
            .withPassword(IDENTITY_DATABASE_PASSWORD);
    postgreSQLContainer.withNetwork(Network.SHARED);
    postgreSQLContainer.withExposedPorts(POSTGRES_PORT);
    postgreSQLContainer.withNetworkAliases(POSTGRES_NETWORK_ALIAS);
    postgreSQLContainer.start();

    testContext.setExternalPostgresHost(postgreSQLContainer.getContainerIpAddress());
    testContext.setExternalPostgresPort(postgreSQLContainer.getMappedPort(POSTGRES_PORT));
    testContext.setInternalPostgresHost(POSTGRES_NETWORK_ALIAS);
    testContext.setInternalPostgresPort(POSTGRES_PORT);
    LOGGER.info(
        "************ Postgres started on {}:{} ************",
        testContext.getExternalPostgresHost(),
        testContext.getExternalPostgresPort());
  }

  public void startOpenSearch(TestContext testContext) {
    LOGGER.info("************ Starting OpenSearch ************");
    osContainer =
        (OpensearchContainer)
            new OpensearchContainer(
                    String.format(
                        "%s:%s", DOCKER_OPENSEARCH_IMAGE_NAME, DOCKER_OPENSEARCH_IMAGE_VERSION))
                .withNetwork(Network.SHARED)
                .withEnv("path.repo", "~/")
                .withNetworkAliases(OS_NETWORK_ALIAS)
                .withExposedPorts(OS_PORT);
    osContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    osContainer.start();

    testContext.setNetwork(Network.SHARED);
    testContext.setExternalOsHost(osContainer.getContainerIpAddress());
    testContext.setExternalOsPort(osContainer.getMappedPort(OS_PORT));
    testContext.setInternalOsHost(OS_NETWORK_ALIAS);
    testContext.setInternalOsPort(OS_PORT);

    LOGGER.info(
        "************ OpenSearch started on {}:{} ************",
        testContext.getExternalOsHost(),
        testContext.getExternalOsPort());
  }

  public void startElasticsearch(TestContext testContext) {
    LOGGER.info("************ Starting Elasticsearch ************");
    elsContainer =
        new ElasticsearchContainer(
                String.format(
                    "%s:%s",
                    DOCKER_ELASTICSEARCH_IMAGE_NAME,
                    ElasticsearchClient.class.getPackage().getImplementationVersion()))
            .withNetwork(Network.SHARED)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("path.repo", "~/")
            .withNetworkAliases(ELS_NETWORK_ALIAS)
            .withExposedPorts(ELS_PORT);
    elsContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    elsContainer.start();

    testContext.setNetwork(Network.SHARED);
    testContext.setExternalElsHost(elsContainer.getContainerIpAddress());
    testContext.setExternalElsPort(elsContainer.getMappedPort(ELS_PORT));
    testContext.setInternalElsHost(ELS_NETWORK_ALIAS);
    testContext.setInternalElsPort(ELS_PORT);

    LOGGER.info(
        "************ Elasticsearch started on {}:{} ************",
        testContext.getExternalElsHost(),
        testContext.getExternalElsPort());
  }

  public String getIdentityClientSecret() {
    try {
      return RetryOperation.<String>newBuilder()
          .noOfRetry(5)
          .retryOn(NotFoundException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message("Trying to get Identity keycloak client secret")
          .retryConsumer(
              () ->
                  keycloakClient
                      .realm("camunda-platform")
                      .clients()
                      .findByClientId("camunda-identity")
                      .get(0)
                      .getSecret())
          .build()
          .retry();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Retryable(
      retryFor = TasklistRuntimeException.class,
      maxAttempts = 5,
      backoff = @Backoff(delay = 3000))
  public void checkElasctisearchHealth(TestContext testContext) {
    try {
      final RestHighLevelClient esClient =
          new RestHighLevelClient(
              RestClient.builder(
                  new HttpHost(
                      testContext.getExternalElsHost(), testContext.getExternalElsPort())));
      final ClusterHealthResponse clusterHealthResponse =
          esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
      final ClusterHealthStatus healthStatus = clusterHealthResponse.getStatus();
      final boolean isHealth = healthStatus.equals(ClusterHealthStatus.GREEN);
      if (isHealth) {
        LOGGER.info("ElasticSearch cluster is up & running, health status: '{}'", healthStatus);
      } else {
        LOGGER.warn("ElasticSearch cluster health status is : '{}'", healthStatus);
      }
    } catch (IOException | ElasticsearchException ex) {
      throw new TasklistRuntimeException();
    }
  }

  @Retryable(
      retryFor = TasklistRuntimeException.class,
      maxAttempts = 5,
      backoff = @Backoff(delay = 3000))
  public void checkOpenSearchHealth(OpenSearchClient osClient) {
    try {
      final HealthResponse healthResponse = osClient.cluster().health();
      final HealthStatus healthStatus = healthResponse.status();
      final boolean isHealth = HealthStatus.Green.equals(healthStatus);
      if (isHealth) {
        LOGGER.info("OpenSearch cluster is up & running, health status: '{}'", healthStatus);
      } else {
        LOGGER.warn("OpenSearch cluster health status is : '{}'", healthStatus);
      }
    } catch (IOException | OpenSearchException ex) {
      throw new TasklistRuntimeException();
    }
  }

  public GenericContainer startTasklist(String version, TestContext testContext) {
    if (tasklistContainer == null) {
      LOGGER.info("************ Starting Tasklist {} ************", version);
      tasklistContainer = createTasklistContainer(version, testContext);
      startTasklistContainer(tasklistContainer, testContext);
      LOGGER.info("************ Tasklist started  ************");
    } else {
      throw new IllegalStateException("Tasklist is already started. Call stopTasklist first.");
    }
    return tasklistContainer;
  }

  public GenericContainer createTasklistContainer(
      String dockerImageName, String version, TestContext testContext) {
    tasklistContainer =
        new GenericContainer<>(String.format("%s:%s", dockerImageName, version))
            .withExposedPorts(8080)
            .withNetwork(testContext.getNetwork())
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(8080)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));
    applyConfiguration(tasklistContainer, testContext);
    return tasklistContainer;
  }

  public GenericContainer createTasklistContainer(String version, TestContext testContext) {
    return createTasklistContainer(DOCKER_TASKLIST_IMAGE_NAME, version, testContext);
  }

  public void startTasklistContainer(GenericContainer tasklistContainer, TestContext testContext) {
    tasklistContainer.start();

    testContext.setExternalTasklistHost(tasklistContainer.getHost());
    testContext.setExternalTasklistPort(tasklistContainer.getMappedPort(TASKLIST_HTTP_PORT));
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> tasklistContainer, TestContext testContext) {
    if (TasklistPropertiesUtil.isOpenSearchDatabase()) {
      final String osHost = testContext.getInternalOsHost();
      final Integer osPort = testContext.getInternalOsPort();
      tasklistContainer
          .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_URL", String.format("http://%s:%s", osHost, osPort))
          .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_HOST", osHost)
          .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_PORT", String.valueOf(osPort))
          .withEnv(
              "CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", String.format("http://%s:%s", osHost, osPort))
          .withEnv("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_UHOST", osHost)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_PORT", String.valueOf(osPort));
      if (testContext.getZeebeIndexPrefix() != null) {
        tasklistContainer.withEnv(
            "CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_PREFIX", testContext.getZeebeIndexPrefix());
      }
    } else {
      final String elsHost = testContext.getInternalElsHost();
      final Integer elsPort = testContext.getInternalElsPort();
      tasklistContainer
          .withEnv(
              "CAMUNDA_TASKLIST_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", elsHost)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", String.valueOf(elsPort))
          .withEnv(
              "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL",
              String.format("http://%s:%s", elsHost, elsPort))
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_UHOST", elsHost)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));
      if (testContext.getZeebeIndexPrefix() != null) {
        tasklistContainer.withEnv(
            "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", testContext.getZeebeIndexPrefix());
      }
    }

    final String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      tasklistContainer.withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
    }
  }

  public ZeebeContainer startZeebe(final String version, TestContext testContext) {
    if (broker == null) {
      LOGGER.info("************ Starting Zeebe {} ************", version);
      broker =
          new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + version))
              .withNetwork(testContext.getNetwork())
              .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
      if (testContext.getZeebeDataFolder() != null) {
        broker.withCreateContainerCmdModifier(
            cmd -> {
              final String zeebeDataFolderPath = testContext.getZeebeDataFolder().getPath();
              final String bindVolume = zeebeDataFolderPath + ":/usr/local/zeebe/data";
              cmd.withUser("1001:0")
                  .getHostConfig()
                  .withBinds(Bind.parse(bindVolume))
                  .withPrivileged(true);
            });
      }
      broker.setWaitStrategy(
          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
      addConfig(broker, testContext);
      broker.start();
      LOGGER.info("************ Zeebe started  ************");

      testContext.setInternalZeebeContactPoint(
          broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
      testContext.setExternalZeebeContactPoint(
          broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  protected void addConfig(ZeebeContainer zeebeBroker, TestContext testContext) {
    if (TasklistPropertiesUtil.isOpenSearchDatabase()) {
      zeebeBroker
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
              "io.camunda.zeebe.exporter.opensearch.OpensearchExporter")
          .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL", "http://opensearch:" + OS_PORT)
          .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE", "1");
      if (testContext.getZeebeIndexPrefix() != null) {
        zeebeBroker.withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX",
            testContext.getZeebeIndexPrefix());
      }
    } else {
      zeebeBroker
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
              "io.camunda.zeebe.exporter.ElasticsearchExporter")
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:" + ELS_PORT)
          .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1");
      if (testContext.getZeebeIndexPrefix() != null) {
        zeebeBroker.withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX",
            testContext.getZeebeIndexPrefix());
      }
    }
  }

  public void stopZeebeAndTasklist(TestContext testContext) {
    stopZeebe(testContext);
    stopTasklist(testContext);
  }

  public void stopIdentity(TestContext testcontext) {
    if (identityContainer != null) {
      identityContainer.close();
    }
    if (postgreSQLContainer != null) {
      postgreSQLContainer.close();
    }
    if (keycloakContainer != null) {
      keycloakContainer.close();
    }
  }

  protected void stopZeebe(TestContext testContext) {
    if (broker != null) {
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  protected void stopTasklist(TestContext testContext) {
    if (tasklistContainer != null) {
      tasklistContainer.close();
      tasklistContainer = null;
    }
    testContext.setExternalTasklistHost(null);
    testContext.setExternalTasklistPort(null);
  }

  @PreDestroy
  public void stopElasticsearch() {
    stopEls();
  }

  private void stopEls() {
    if (elsContainer != null) {
      elsContainer.stop();
    }
  }
}
