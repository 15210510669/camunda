package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.cutTrailingSlash;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;

public class ConfigurationService {
  public static final String DOC_URL = MessageFormat.format(
    "https://docs.camunda.org/optimize/{0}.{1}",
    Version.VERSION_MAJOR,
    Version.VERSION_MINOR
  );
  private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
  private static final String ENGINES_FIELD = "engines";
  private static final String ENGINE_REST_PATH = "/engine/";
  private static final String[] DEFAULT_CONFIG_LOCATIONS = {"service-config.yaml", "environment-config.yaml"};
  private static final String[] DEFAULT_DEPRECATED_CONFIG_LOCATIONS = {"deprecated-config.yaml"};
  private ReadContext configJsonContext;
  private Map<String, String> deprecatedConfigKeys;

  private Map<String, EngineConfiguration> configuredEngines;
  private Integer tokenLifeTime;
  private String userValidationEndpoint;
  private String processDefinitionEndpoint;
  private String processDefinitionXmlEndpoint;
  private String decisionDefinitionEndpoint;
  private String decisionDefinitionXmlEndpoint;

  private String engineDateFormat;
  private Long importHandlerWait;
  private Long maximumBackoff;

  // elasticsearch connection
  private List<ElasticsearchConnectionNodeConfiguration> elasticsearchConnectionNodes;
  private Integer elasticsearchScrollTimeout;
  private Integer elasticsearchConnectionTimeout;

  // elasticsearch connection security
  private String elasticsearchSecurityUsername;
  private String elasticsearchSecurityPassword;
  private Boolean elasticsearchSecuritySSLEnabled;
  private String elasticsearchSecuritySSLKey;
  private String elasticsearchSecuritySSLCertificate;
  private List<String> elasticsearchSecuritySSLCertificateAuthorities;
  private String elasticsearchSecuritySSLVerificationMode;

  // elasticsearch cluster settings
  private String elasticSearchClusterName;
  private Integer esNumberOfReplicas;
  private Integer esNumberOfShards;
  private String esRefreshInterval;
  private Long samplerInterval;

  // job executor settings
  private Integer elasticsearchJobExecutorQueueSize;
  private Integer elasticsearchJobExecutorThreadCount;

  // engine import settings
  private Integer engineConnectTimeout;
  private Integer engineReadTimeout;
  private Integer currentTimeBackoffMilliseconds;
  private Integer engineImportProcessInstanceMaxPageSize;
  private Integer engineImportVariableInstanceMaxPageSize;
  private Integer engineImportProcessDefinitionXmlMaxPageSize;
  private Integer engineImportDecisionDefinitionXmlMaxPageSize;
  private Integer engineImportDecisionInstanceMaxPageSize;
  private Integer importIndexAutoStorageIntervalInSec;
  private Boolean importDmnDataEnabled;

  // plugin base packages
  private List<String> variableImportPluginBasePackages;
  private List<String> engineRestFilterPluginBasePackages;
  private List<String> authenticationExtractorPluginBasePackages;

  private Long engineImportActivityInstanceMaxPageSize;
  private String containerHost;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Integer containerHttpsPort;
  private Integer containerHttpPort;

  private Integer maxStatusConnections;
  private Boolean checkMetadata;

  private Boolean emailEnabled;
  private String alertEmailUsername;
  private String alertEmailPassword;
  private String alertEmailAddress;
  private String alertEmailHostname;
  private Integer alertEmailPort;
  private String alertEmailProtocol;

  private Integer exportCsvLimit;
  private Integer exportCsvOffset;

  private Properties quartzProperties;

  // history cleanup
  private OptimizeCleanupConfiguration cleanupServiceConfiguration;

  private Boolean sharingEnabled;

  public ConfigurationService() {
    this((String[]) null, null);
  }

  public ConfigurationService(String[] sources) {
    this(sources, null);
  }

  public ConfigurationService(List<InputStream> sources, List<InputStream> deprecatedConfigLocations) {
    initConfigurationContexts(sources, deprecatedConfigLocations);
  }

  public ConfigurationService(String[] configLocations, String[] deprecatedConfigLocations) {
    this(
      getLocationsAsInputStream(configLocations == null ? DEFAULT_CONFIG_LOCATIONS : configLocations),
      getLocationsAsInputStream(deprecatedConfigLocations == null ? DEFAULT_DEPRECATED_CONFIG_LOCATIONS :
                                  deprecatedConfigLocations)
    );
  }

  private static List<InputStream> getLocationsAsInputStream(String[] locationsToUse) {
    List<InputStream> sources = new ArrayList<>();
    for (String location : locationsToUse) {
      InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }
    return sources;
  }

  private static InputStream wrapInputStream(String location) {
    return ConfigurationService.class.getClassLoader().getResourceAsStream(location);
  }

  public void validateNoDeprecatedConfigKeysUsed() {
    final Configuration conf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    final DocumentContext failsafeConfigurationJsonContext = JsonPath.using(conf)
      .parse((Object) configJsonContext.json());

    final Map<String, String> usedDeprecationKeysWithNewDocumentationPath = deprecatedConfigKeys.entrySet().stream()
      .filter(entry -> Optional.ofNullable(failsafeConfigurationJsonContext.read("$." + entry.getKey()))
        // in case of array structures we always a list as result, thus we need to check if it contains actual results
        .flatMap(object -> object instanceof Collection && ((Collection) object).size() == 0
          ? Optional.empty()
          : Optional.of(object)
        )
        .isPresent()
      ).map(keyAndPath -> {
        keyAndPath.setValue(DOC_URL + keyAndPath.getValue());
        return keyAndPath;
      })
      .peek(keyAndUrl -> logger.error(
        "Deprecated setting used with key {}, please checkout the updated documentation {}",
        keyAndUrl.getKey(), keyAndUrl.getValue()
      ))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!usedDeprecationKeysWithNewDocumentationPath.isEmpty()) {
      throw new OptimizeConfigurationException(
        "Configuration contains deprecated entries", usedDeprecationKeysWithNewDocumentationPath
      );
    }
  }

  private void initConfigurationContexts(List<InputStream> configLocationsToUse,
                                         List<InputStream> deprecatedConfigLocationsToUse) {
    this.configJsonContext = parseConfigFromLocations(configLocationsToUse, configureConfigMapper())
      .orElseThrow(() -> new OptimizeConfigurationException("No single configuration source could be read"));
    this.deprecatedConfigKeys = (Map<String, String>)
      parseConfigFromLocations(
        deprecatedConfigLocationsToUse,
        configureConfigMapper()
      ).map(ReadContext::json).orElse(Collections.emptyMap());
  }

  private static void merge(JsonNode mainNode, JsonNode updateNode) {
    if (updateNode == null) {
      return;
    }

    Iterator<String> fieldNames = updateNode.fieldNames();
    while (fieldNames.hasNext()) {

      String fieldName = fieldNames.next();
      JsonNode jsonNode = mainNode.get(fieldName);
      // if field exists and is an embedded object
      if (jsonNode != null && jsonNode.isObject() && !ENGINES_FIELD.equals(fieldName)) {
        merge(jsonNode, updateNode.get(fieldName));
      } else if (jsonNode != null && jsonNode.isObject() && ENGINES_FIELD.equals(fieldName)) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      } else if (mainNode instanceof ObjectNode) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      }

    }
  }

  private static void overwriteField(ObjectNode mainNode, JsonNode updateNode, String fieldName) {
    JsonNode value = updateNode.get(fieldName);
    mainNode.put(fieldName, value);
  }

  private Optional<DocumentContext> parseConfigFromLocations(List<InputStream> sources, YAMLMapper yamlMapper) {
    try {
      if (sources.isEmpty()) {
        return Optional.empty();
      }
      //read default values from the first location
      JsonNode resultNode = yamlMapper.readTree(sources.remove(0));
      //read with overriding default values all locations
      for (InputStream inputStream : sources) {
        merge(resultNode, yamlMapper.readTree(inputStream));
      }

      //prepare to work with JSON Path
      return Optional.of(JsonPath.parse(yamlMapper.convertValue(resultNode, HashMap.class)));
    } catch (IOException e) {
      logger.error("error reading configuration", e);
      return Optional.empty();
    }
  }

  private YAMLMapper configureConfigMapper() {
    final YAMLMapper yamlMapper = new YAMLMapper();
    yamlMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    // to parse dates
    yamlMapper.registerModule(new JavaTimeModule());
    //configure Jackson as provider in order to be able to use TypeRef objects
    //during serialization process
    Configuration.setDefaults(new Configuration.Defaults() {
      private final ObjectMapper objectMapper =
        new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

      private final JsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
      private final MappingProvider mappingProvider = new JacksonMappingProvider(objectMapper);

      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }

      @Override
      public Set<Option> options() {
        return EnumSet.noneOf(Option.class);
      }
    });
    return yamlMapper;
  }

  public Map<String, EngineConfiguration> getConfiguredEngines() {
    if (configuredEngines == null) {
      TypeRef<HashMap<String, EngineConfiguration>> typeRef = new TypeRef<HashMap<String, EngineConfiguration>>() {
      };
      configuredEngines = configJsonContext.read(ConfigurationServiceConstants.CONFIGURED_ENGINES, typeRef);
      configuredEngines.forEach((k, v) -> {
        v.setRest(cutTrailingSlash(v.getRest()));
        v.getWebapps().setEndpoint(cutTrailingSlash(v.getWebapps().getEndpoint()));
      });
    }
    return configuredEngines;
  }

  public Integer getTokenLifeTime() {
    if (tokenLifeTime == null) {
      tokenLifeTime = configJsonContext.read(ConfigurationServiceConstants.TOKEN_LIFE_TIME);
    }
    return tokenLifeTime;
  }

  public List<ElasticsearchConnectionNodeConfiguration> getElasticsearchConnectionNodes() {
    if (elasticsearchConnectionNodes == null) {
      // @formatter:off
      TypeRef<List<ElasticsearchConnectionNodeConfiguration>> typeRef =
        new TypeRef<List<ElasticsearchConnectionNodeConfiguration>>() {};
      // @formatter:on
      elasticsearchConnectionNodes =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_NODES, typeRef);
    }
    return elasticsearchConnectionNodes;
  }

  public String getElasticSearchClusterName() {
    if (elasticSearchClusterName == null) {
      elasticSearchClusterName = configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_CLUSTER_NAME);
    }
    return elasticSearchClusterName;
  }

  public String getUserValidationEndpoint() {
    if (userValidationEndpoint == null) {
      userValidationEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.USER_VALIDATION_ENDPOINT)
      );
    }
    return userValidationEndpoint;
  }

  public String getProcessDefinitionEndpoint() {
    if (processDefinitionEndpoint == null) {
      processDefinitionEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_ENDPOINT)
      );
    }
    return processDefinitionEndpoint;
  }

  public String getDecisionDefinitionEndpoint() {
    if (decisionDefinitionEndpoint == null) {
      decisionDefinitionEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.DECISION_DEFINITION_ENDPOINT)
      );
    }
    return decisionDefinitionEndpoint;
  }

  public String getDecisionDefinitionXmlEndpoint(String decisionDefinitionId) {
    return getDecisionDefinitionEndpoint() + "/" + decisionDefinitionId + getDecisionDefinitionXmlEndpoint();
  }

  public String getEngineDateFormat() {
    if (engineDateFormat == null) {
      engineDateFormat = configJsonContext.read(ConfigurationServiceConstants.ENGINE_DATE_FORMAT);
    }
    return engineDateFormat;
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec =
        configJsonContext.read(ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class);
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public long getImportHandlerWait() {
    if (importHandlerWait == null) {
      importHandlerWait = configJsonContext.read(ConfigurationServiceConstants.IMPORT_HANDLER_INTERVAL, Long.class);
    }
    return importHandlerWait;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff = configJsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public int getElasticsearchJobExecutorQueueSize() {
    if (elasticsearchJobExecutorQueueSize == null) {
      elasticsearchJobExecutorQueueSize = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_MAX_JOB_QUEUE_SIZE,
        Integer.class
      );
    }
    return elasticsearchJobExecutorQueueSize;
  }

  public int getElasticsearchJobExecutorThreadCount() {
    if (elasticsearchJobExecutorThreadCount == null) {
      elasticsearchJobExecutorThreadCount = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT,
        Integer.class
      );
    }
    return elasticsearchJobExecutorThreadCount;
  }

  public int getElasticsearchScrollTimeout() {
    if (elasticsearchScrollTimeout == null) {
      elasticsearchScrollTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_SCROLL_TIMEOUT,
        Integer.class
      );
    }
    return elasticsearchScrollTimeout;
  }

  public int getElasticsearchConnectionTimeout() {
    if (elasticsearchConnectionTimeout == null) {
      elasticsearchConnectionTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_TIMEOUT,
        Integer.class
      );
    }
    return elasticsearchConnectionTimeout;
  }

  public int getEngineConnectTimeout() {
    if (engineConnectTimeout == null) {
      engineConnectTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_CONNECT_TIMEOUT,
        Integer.class
      );
    }
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    if (engineReadTimeout == null) {
      engineReadTimeout = configJsonContext.read(ConfigurationServiceConstants.ENGINE_READ_TIMEOUT, Integer.class);
    }
    return engineReadTimeout;
  }

  public int getCurrentTimeBackoffMilliseconds() {
    if (currentTimeBackoffMilliseconds == null) {
      currentTimeBackoffMilliseconds = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS,
        Integer.class
      );
    }
    return currentTimeBackoffMilliseconds;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    if (engineImportProcessInstanceMaxPageSize == null) {
      engineImportProcessInstanceMaxPageSize =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    if (engineImportVariableInstanceMaxPageSize == null) {
      engineImportVariableInstanceMaxPageSize =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public String getEsRefreshInterval() {
    if (esRefreshInterval == null) {
      esRefreshInterval = configJsonContext.read(ConfigurationServiceConstants.ES_REFRESH_INTERVAL);
    }
    return esRefreshInterval;
  }

  public int getEsNumberOfReplicas() {
    if (esNumberOfReplicas == null) {
      esNumberOfReplicas = configJsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_REPLICAS);
    }
    return esNumberOfReplicas;
  }

  public int getEsNumberOfShards() {
    if (esNumberOfShards == null) {
      esNumberOfShards = configJsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_SHARDS);
    }
    return esNumberOfShards;
  }

  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE);
    }
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public String getProcessDefinitionXmlEndpoint() {
    if (processDefinitionXmlEndpoint == null) {
      processDefinitionXmlEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_ENDPOINT)
      );
    }
    return processDefinitionXmlEndpoint;
  }

  public Boolean getSharingEnabled() {
    if (sharingEnabled == null) {
      sharingEnabled = configJsonContext.read(ConfigurationServiceConstants.SHARING_ENABLED);
    }
    return sharingEnabled;
  }

  public String getDecisionDefinitionXmlEndpoint() {
    if (decisionDefinitionXmlEndpoint == null) {
      decisionDefinitionXmlEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.DECISION_DEFINITION_XML_ENDPOINT)
      );
    }
    return decisionDefinitionXmlEndpoint;
  }

  public int getEngineImportDecisionDefinitionXmlMaxPageSize() {
    if (engineImportDecisionDefinitionXmlMaxPageSize == null) {
      engineImportDecisionDefinitionXmlMaxPageSize =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE);
    }
    return engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public int getEngineImportDecisionInstanceMaxPageSize() {
    if (engineImportDecisionInstanceMaxPageSize == null) {
      engineImportDecisionInstanceMaxPageSize =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportDecisionInstanceMaxPageSize);
    return engineImportDecisionInstanceMaxPageSize;
  }

  public long getSamplerInterval() {
    if (samplerInterval == null) {
      samplerInterval = configJsonContext.read(ConfigurationServiceConstants.SAMPLER_INTERVAL, Long.class);
    }
    return samplerInterval;
  }

  public List<String> getVariableImportPluginBasePackages() {
    if (variableImportPluginBasePackages == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {
      };
      variableImportPluginBasePackages =
        configJsonContext.read(ConfigurationServiceConstants.VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES, typeRef);
    }
    return variableImportPluginBasePackages;
  }

  public List<String> getEngineRestFilterPluginBasePackages() {
    if (engineRestFilterPluginBasePackages == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {
      };
      engineRestFilterPluginBasePackages =
        configJsonContext.read(ConfigurationServiceConstants.ENGINE_REST_FILTER_PLUGIN_BASE_PACKAGES, typeRef);
    }
    return engineRestFilterPluginBasePackages;
  }

  public List<String> getAuthenticationExtractorPluginBasePackages() {
    if (authenticationExtractorPluginBasePackages == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {
      };
      authenticationExtractorPluginBasePackages =
        configJsonContext.read(ConfigurationServiceConstants.AUTHENTICATION_EXTRACTOR_BASE_PACKAGES, typeRef);
    }
    return authenticationExtractorPluginBasePackages;
  }

  public long getEngineImportActivityInstanceMaxPageSize() {
    if (engineImportActivityInstanceMaxPageSize == null) {
      engineImportActivityInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE,
        Long.class
      );
    }
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  public String getContainerKeystoreLocation() {
    if (containerKeystoreLocation == null) {
      containerKeystoreLocation = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_LOCATION);
      // we need external form here for the path to work if the keystore is inside the jar (default)
      containerKeystoreLocation = ConfigurationUtil.resolvePathAsAbsoluteUrl(containerKeystoreLocation)
        .toExternalForm();
    }
    return containerKeystoreLocation;
  }

  public int getContainerHttpsPort() {
    if (containerHttpsPort == null) {
      containerHttpsPort = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTPS_PORT);
    }
    return containerHttpsPort;
  }

  public int getContainerHttpPort() {
    if (containerHttpPort == null) {
      containerHttpPort = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTP_PORT);
    }
    return containerHttpPort;
  }

  public int getMaxStatusConnections() {
    if (maxStatusConnections == null) {
      maxStatusConnections = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_STATUS_MAX_CONNECTIONS);
    }
    return maxStatusConnections;
  }

  public Boolean getCheckMetadata() {
    if (checkMetadata == null) {
      checkMetadata = configJsonContext.read(ConfigurationServiceConstants.CHECK_METADATA);
    }
    return checkMetadata;
  }

  public String getProcessDefinitionXmlEndpoint(String processDefinitionId) {
    return getProcessDefinitionEndpoint() + "/" + processDefinitionId + getProcessDefinitionXmlEndpoint();
  }

  public String getEngineRestApiEndpointOfCustomEngine(String engineAlias) {
    return this.getEngineRestApiEndpoint(engineAlias) + ENGINE_REST_PATH + getEngineName(engineAlias);
  }

  public String getDefaultEngineAuthenticationUser(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().getUser();
  }

  public String getDefaultEngineAuthenticationPassword(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().getPassword();
  }

  /**
   * This method is mostly for internal usage. All API invocations
   * should rely on
   * {@link org.camunda.optimize.service.util.configuration.ConfigurationService#getEngineRestApiEndpointOfCustomEngine(java.lang.String)}
   *
   * @param engineAlias - an alias of configured engine
   * @return <b>raw</b> REST endpoint, without engine suffix
   */
  public String getEngineRestApiEndpoint(String engineAlias) {
    return getEngineConfiguration(engineAlias).getRest();
  }

  public String getEngineName(String engineAlias) {
    return getEngineConfiguration(engineAlias).getName();
  }

  private EngineConfiguration getEngineConfiguration(String engineAlias) {
    return this.getConfiguredEngines().get(engineAlias);
  }

  public Properties getQuartzProperties() {
    if (quartzProperties == null) {
      quartzProperties = new Properties();
      quartzProperties.put(
        "org.quartz.jobStore.class",
        configJsonContext.read(ConfigurationServiceConstants.QUARTZ_JOB_STORE_CLASS)
      );
    }
    return quartzProperties;
  }

  public String getAlertEmailUsername() {
    if (alertEmailUsername == null) {
      alertEmailUsername = configJsonContext.read(ConfigurationServiceConstants.EMAIL_USERNAME);
    }
    return alertEmailUsername;
  }

  public String getAlertEmailPassword() {
    if (alertEmailPassword == null) {
      alertEmailPassword = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PASSWORD);
    }
    return alertEmailPassword;
  }

  public boolean isEmailEnabled() {
    if (emailEnabled == null) {
      emailEnabled = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED);
    }
    return emailEnabled;
  }

  public Boolean isImportDmnDataEnabled() {
    if (importDmnDataEnabled == null) {
      importDmnDataEnabled = configJsonContext.read(ConfigurationServiceConstants.IMPORT_DMN_DATA);
    }
    return importDmnDataEnabled;
  }

  public String getAlertEmailAddress() {
    if (alertEmailAddress == null) {
      alertEmailAddress = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return alertEmailAddress;
  }

  public String getAlertEmailHostname() {
    if (alertEmailHostname == null) {
      alertEmailHostname = configJsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return alertEmailHostname;
  }

  public Integer getAlertEmailPort() {
    if (alertEmailPort == null) {
      alertEmailPort = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PORT);
    }
    return alertEmailPort;
  }

  public String getAlertEmailProtocol() {
    if (alertEmailProtocol == null) {
      alertEmailProtocol = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PROTOCOL);
    }
    return alertEmailProtocol;
  }

  public Integer getExportCsvLimit() {
    if (exportCsvLimit == null) {
      exportCsvLimit = configJsonContext.read(ConfigurationServiceConstants.EXPORT_CSV_LIMIT);
    }
    return exportCsvLimit;
  }

  public Integer getExportCsvOffset() {
    if (exportCsvOffset == null) {
      exportCsvOffset = configJsonContext.read(ConfigurationServiceConstants.EXPORT_CSV_OFFSET);
    }
    return exportCsvOffset;
  }

  public String getElasticsearchSecurityUsername() {
    if (elasticsearchSecurityUsername == null) {
      elasticsearchSecurityUsername =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_USERNAME);
    }
    return elasticsearchSecurityUsername;
  }

  public String getElasticsearchSecurityPassword() {
    if (elasticsearchSecurityPassword == null) {
      elasticsearchSecurityPassword =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_PASSWORD);
    }
    return elasticsearchSecurityPassword;
  }

  public Boolean getElasticsearchSecuritySSLEnabled() {
    if (elasticsearchSecuritySSLEnabled == null) {
      elasticsearchSecuritySSLEnabled =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_ENABLED);
    }
    return elasticsearchSecuritySSLEnabled;
  }

  public String getElasticsearchSecuritySSLKey() {
    if (elasticsearchSecuritySSLKey == null && getElasticsearchSecuritySSLEnabled()) {
      elasticsearchSecuritySSLKey =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_KEY);
      elasticsearchSecuritySSLKey = resolvePathAsAbsoluteUrl(elasticsearchSecuritySSLKey).getPath();
    }
    return elasticsearchSecuritySSLKey;
  }

  public String getElasticsearchSecuritySSLCertificate() {
    if (elasticsearchSecuritySSLCertificate == null && getElasticsearchSecuritySSLEnabled()) {
      elasticsearchSecuritySSLCertificate =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE);
      elasticsearchSecuritySSLCertificate =
        resolvePathAsAbsoluteUrl(elasticsearchSecuritySSLCertificate).getPath();
    }
    return elasticsearchSecuritySSLCertificate;
  }

  public List<String> getElasticsearchSecuritySSLCertificateAuthorities() {
    if (elasticsearchSecuritySSLCertificateAuthorities == null && getElasticsearchSecuritySSLEnabled()) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {
      };
      List<String> authoritiesAsList =
        configJsonContext.read(ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES, typeRef);
      elasticsearchSecuritySSLCertificateAuthorities =
        authoritiesAsList.stream().map(a -> resolvePathAsAbsoluteUrl(a).getPath()).collect(Collectors.toList());
    }
    return elasticsearchSecuritySSLCertificateAuthorities;
  }

  public OptimizeCleanupConfiguration getCleanupServiceConfiguration() {
    if (cleanupServiceConfiguration == null) {
      cleanupServiceConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.HISTORY_CLEANUP,
        OptimizeCleanupConfiguration.class
      );
      cleanupServiceConfiguration.validate();
    }
    return cleanupServiceConfiguration;
  }

  public void setMaxStatusConnections(Integer maxStatusConnections) {
    this.maxStatusConnections = maxStatusConnections;
  }

  public void setExportCsvLimit(Integer exportCsvLimit) {
    this.exportCsvLimit = exportCsvLimit;
  }

  public void setExportCsvOffset(Integer exportCsvOffset) {
    this.exportCsvOffset = exportCsvOffset;
  }

  public void setVariableImportPluginBasePackages(List<String> variableImportPluginBasePackages) {
    this.variableImportPluginBasePackages = variableImportPluginBasePackages;
  }

  public void setEngineRestFilterPluginBasePackages(List<String> engineRestFilterPluginBasePackages) {
    this.engineRestFilterPluginBasePackages = engineRestFilterPluginBasePackages;
  }

  public void setAuthenticationExtractorPluginBasePackages(List<String> authenticationExtractorPluginBasePackages) {
    this.authenticationExtractorPluginBasePackages = authenticationExtractorPluginBasePackages;
  }

  public void setConfiguredEngines(Map<String, EngineConfiguration> configuredEngines) {
    this.configuredEngines = configuredEngines;
  }

  public static String getEnginesField() {
    return ENGINES_FIELD;
  }

  public static String getEngineRestPath() {
    return ENGINE_REST_PATH;
  }

  public String getElasticsearchSecuritySSLVerificationMode() {
    if (elasticsearchSecuritySSLVerificationMode == null) {
      elasticsearchSecuritySSLVerificationMode =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_VERIFICATION_MODE);
    }
    return elasticsearchSecuritySSLVerificationMode;
  }

  public void setSharingEnabled(Boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
  }

  public void setConfigJsonContext(ReadContext configJsonContext) {
    this.configJsonContext = configJsonContext;
  }

  public void setTokenLifeTime(Integer tokenLifeTime) {
    this.tokenLifeTime = tokenLifeTime;
  }

  public void setElasticSearchClusterName(String elasticSearchClusterName) {
    this.elasticSearchClusterName = elasticSearchClusterName;
  }

  public void setUserValidationEndpoint(String userValidationEndpoint) {
    this.userValidationEndpoint = userValidationEndpoint;
  }

  public void setProcessDefinitionEndpoint(String processDefinitionEndpoint) {
    this.processDefinitionEndpoint = processDefinitionEndpoint;
  }

  public void setImportIndexAutoStorageIntervalInSec(Integer importIndexAutoStorageIntervalInSec) {
    this.importIndexAutoStorageIntervalInSec = importIndexAutoStorageIntervalInSec;
  }

  public void setEngineDateFormat(String engineDateFormat) {
    this.engineDateFormat = engineDateFormat;
  }

  public void setImportHandlerWait(Long importHandlerWait) {
    this.importHandlerWait = importHandlerWait;
  }

  public void setMaximumBackoff(Long maximumBackoff) {
    this.maximumBackoff = maximumBackoff;
  }

  public void setElasticsearchJobExecutorQueueSize(Integer elasticsearchJobExecutorQueueSize) {
    this.elasticsearchJobExecutorQueueSize = elasticsearchJobExecutorQueueSize;
  }

  public void setElasticsearchJobExecutorThreadCount(Integer elasticsearchJobExecutorThreadCount) {
    this.elasticsearchJobExecutorThreadCount = elasticsearchJobExecutorThreadCount;
  }

  public void setElasticsearchScrollTimeout(Integer elasticsearchScrollTimeout) {
    this.elasticsearchScrollTimeout = elasticsearchScrollTimeout;
  }

  public void setElasticsearchConnectionTimeout(Integer elasticsearchConnectionTimeout) {
    this.elasticsearchConnectionTimeout = elasticsearchConnectionTimeout;
  }

  public void setEngineConnectTimeout(Integer engineConnectTimeout) {
    this.engineConnectTimeout = engineConnectTimeout;
  }

  public void setEngineReadTimeout(Integer engineReadTimeout) {
    this.engineReadTimeout = engineReadTimeout;
  }

  public void setCurrentTimeBackoffMilliseconds(Integer currentTimeBackoffMilliseconds) {
    this.currentTimeBackoffMilliseconds = currentTimeBackoffMilliseconds;
  }

  public void setEngineImportProcessInstanceMaxPageSize(Integer engineImportProcessInstanceMaxPageSize) {
    this.engineImportProcessInstanceMaxPageSize = engineImportProcessInstanceMaxPageSize;
  }

  public void setEngineImportVariableInstanceMaxPageSize(Integer engineImportVariableInstanceMaxPageSize) {
    this.engineImportVariableInstanceMaxPageSize = engineImportVariableInstanceMaxPageSize;
  }

  public void setEsRefreshInterval(String esRefreshInterval) {
    this.esRefreshInterval = esRefreshInterval;
  }

  public void setEsNumberOfReplicas(Integer esNumberOfReplicas) {
    this.esNumberOfReplicas = esNumberOfReplicas;
  }

  public void setEsNumberOfShards(Integer esNumberOfShards) {
    this.esNumberOfShards = esNumberOfShards;
  }

  public void setEngineImportProcessDefinitionXmlMaxPageSize(Integer engineImportProcessDefinitionXmlMaxPageSize) {
    this.engineImportProcessDefinitionXmlMaxPageSize = engineImportProcessDefinitionXmlMaxPageSize;
  }

  public void setEngineImportDecisionDefinitionXmlMaxPageSize(Integer engineImportDecisionDefinitionXmlMaxPageSize) {
    this.engineImportDecisionDefinitionXmlMaxPageSize = engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public void setProcessDefinitionXmlEndpoint(String processDefinitionXmlEndpoint) {
    this.processDefinitionXmlEndpoint = processDefinitionXmlEndpoint;
  }

  public void setSamplerInterval(Long samplerInterval) {
    this.samplerInterval = samplerInterval;
  }

  public void setEngineImportActivityInstanceMaxPageSize(Long engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
  }

  public void setCleanupServiceConfiguration(OptimizeCleanupConfiguration cleanupServiceConfiguration) {
    this.cleanupServiceConfiguration = cleanupServiceConfiguration;
  }

  public void setContainerHost(String containerHost) {
    this.containerHost = containerHost;
  }

  public void setContainerKeystorePassword(String containerKeystorePassword) {
    this.containerKeystorePassword = containerKeystorePassword;
  }

  public void setContainerKeystoreLocation(String containerKeystoreLocation) {
    this.containerKeystoreLocation = containerKeystoreLocation;
  }

  public void setContainerHttpsPort(Integer containerHttpsPort) {
    this.containerHttpsPort = containerHttpsPort;
  }

  public void setContainerHttpPort(Integer containerHttpPort) {
    this.containerHttpPort = containerHttpPort;
  }

  public void setCheckMetadata(Boolean checkMetadata) {
    this.checkMetadata = checkMetadata;
  }

  public void setAlertEmailUsername(String alertEmailUsername) {
    this.alertEmailUsername = alertEmailUsername;
  }

  public void setEmailEnabled(Boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public void setAlertEmailPassword(String alertEmailPassword) {
    this.alertEmailPassword = alertEmailPassword;
  }

  public void setAlertEmailAddress(String alertEmailAddress) {
    this.alertEmailAddress = alertEmailAddress;
  }

  public void setAlertEmailHostname(String alertEmailHostname) {
    this.alertEmailHostname = alertEmailHostname;
  }

  public void setAlertEmailPort(Integer alertEmailPort) {
    this.alertEmailPort = alertEmailPort;
  }

  public void setAlertEmailProtocol(String alertEmailProtocol) {
    this.alertEmailProtocol = alertEmailProtocol;
  }

  public void setElasticsearchSecurityUsername(String elasticsearchSecurityUsername) {
    this.elasticsearchSecurityUsername = elasticsearchSecurityUsername;
  }

  public void setElasticsearchSecurityPassword(String elasticsearchSecurityPassword) {
    this.elasticsearchSecurityPassword = elasticsearchSecurityPassword;
  }

  public void setElasticsearchSecuritySSLEnabled(Boolean elasticsearchSecuritySSLEnabled) {
    this.elasticsearchSecuritySSLEnabled = elasticsearchSecuritySSLEnabled;
  }

  public void setElasticsearchSecuritySSLKey(String elasticsearchSecuritySSLKey) {
    this.elasticsearchSecuritySSLKey = elasticsearchSecuritySSLKey;
  }

  public void setElasticsearchSecuritySSLCertificate(String elasticsearchSecuritySSLCertificate) {
    this.elasticsearchSecuritySSLCertificate = elasticsearchSecuritySSLCertificate;
  }

  public void setElasticsearchSecuritySSLCertificateAuthorities(List<String> elasticsearchSecuritySSLCertificateAuthorities) {
    this.elasticsearchSecuritySSLCertificateAuthorities = elasticsearchSecuritySSLCertificateAuthorities;
  }

  public void setElasticsearchSecuritySSLVerificationMode(String elasticsearchSecuritySSLVerificationMode) {
    this.elasticsearchSecuritySSLVerificationMode = elasticsearchSecuritySSLVerificationMode;
  }

  public void setElasticsearchConnectionNodes(List<ElasticsearchConnectionNodeConfiguration> elasticsearchConnectionNodes) {
    this.elasticsearchConnectionNodes = elasticsearchConnectionNodes;
  }

  public void setImportDmnDataEnabled(Boolean importDmnDataEnabled) {
    this.importDmnDataEnabled = importDmnDataEnabled;
  }
}
