package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

public class ConfigurationService {

  private static final String ENGINES_FIELD = "engines";
  private static final String ENGINE_REST_PATH = "/engine/";
  private final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

  private static final String[] DEFAULT_LOCATIONS = { "service-config.yaml", "environment-config.yaml" };
  private ObjectMapper objectMapper;
  private HashMap defaults = null;
  private ReadContext jsonContext;


  private Map<String, EngineConfiguration> configuredEngines;
  private Integer tokenLifeTime;
  private String elasticSearchHost;
  private Integer elasticSearchPort;
  private String optimizeIndex;
  private String haiEndpoint;
  private String userValidationEndpoint;
  private String processDefinitionEndpoint;

  private String eventType;
  private String processDefinitionType;
  private String processDefinitionXmlType;
  private String importIndexType;
  private String durationHeatmapTargetValueType;
  private String variableType;
  private String processInstanceType;
  private String licenseType;
  private String reportType;
  private String dashboardType;
  private String finishedPiIdTrackingType;
  private String unfinishedPiIdTrackingType;
  private String alertType;
  private String reportShareType;
  private String dashboardShareType;
  private String metaDataType;

  private String analyzerName;
  private String tokenizer;
  private String tokenFilter;
  private String engineDateFormat;
  private String optimizeDateFormat;
  private Long importHandlerWait;
  private Long maximumBackoff;
  private Boolean backoffEnabled;
  private Integer elasticsearchJobExecutorQueueSize;
  private Integer elasticsearchJobExecutorThreadCount;
  private String hpiEndpoint;
  private Integer elasticsearchScrollTimeout;
  private Integer elasticsearchConnectionTimeout;
  private Integer engineConnectTimeout;
  private Integer engineReadTimeout;
  private Integer engineImportProcessInstanceMaxPageSize;
  private Integer engineImportVariableInstanceMaxPageSize;
  private String esRefreshInterval;
  private Integer esNumberOfReplicas;
  private Integer esNumberOfShards;
  private Integer engineImportProcessDefinitionXmlMaxPageSize;
  private String processDefinitionXmlEndpoint;
  private Integer importIndexAutoStorageIntervalInSec;
  private Long samplerInterval;
  private List<String> variableImportPluginBasePackages;

  private Integer numberOfRetriesOnConflict;
  private Long engineImportActivityInstanceMaxPageSize;
  private String containerHost;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Integer containerHttpsPort;
  private Integer containerHttpPort;

  private Integer maxStatusConnections;
  private Boolean checkMetadata;

  private Boolean emailsEnabled;
  private String alertEmailUsername;
  private String alertEmailPassword;
  private String alertEmailAddress;
  private String alertEmailHostname;
  private Integer alertEmailPort;
  private String alertEmailProtocol;

  private Integer exportCsvLimit;
  private Integer exportCsvOffset;

  private Properties quartzProperties;


  public ConfigurationService() {
    this((String[]) null);
  }

  public ConfigurationService(String[] locations) {
    String[] locationsToUse = locations == null ? DEFAULT_LOCATIONS : locations;

    //prepare streams for locations
    List<InputStream> sources = new ArrayList<>();
    for (String location : locationsToUse) {
      InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }

    initFromStreams(sources);
  }

  public ConfigurationService(List<InputStream> sources) {
    initFromStreams(sources);
  }

  public void initFromStreams(List<InputStream> sources) {
    objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS,true);
    //read default values from the first location
    try {
      //configure Jackson as provider in order to be able to use TypeRef objects
      //during serialization process
      Configuration.setDefaults(new Configuration.Defaults() {

        private final JsonProvider jsonProvider = new JacksonJsonProvider();
        private final MappingProvider mappingProvider = new JacksonMappingProvider();

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

      JsonNode resultNode = objectMapper.readTree(sources.remove(0));
      //read with overriding default values all locations
      for (InputStream inputStream : sources) {
        merge(resultNode, objectMapper.readTree(inputStream));
      }

      defaults = objectMapper.convertValue(resultNode, HashMap.class);
    } catch (IOException e) {
      logger.error("error reading configuration", e);
    }

    //prepare to work with JSON Path
    jsonContext = JsonPath.parse(defaults);
  }

  public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

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

    return mainNode;
  }

  private static void overwriteField(ObjectNode mainNode, JsonNode updateNode, String fieldName) {
    JsonNode value = updateNode.get(fieldName);
    mainNode.put(fieldName, value);
  }

  private InputStream wrapInputStream(String location) {
    return this.getClass().getClassLoader().getResourceAsStream(location);
  }

  public Map<String, EngineConfiguration> getConfiguredEngines() {
    if (configuredEngines == null) {
      TypeRef<HashMap<String, EngineConfiguration>> typeRef = new TypeRef<HashMap<String, EngineConfiguration>>() {};
      configuredEngines = jsonContext.read(ConfigurationServiceConstants.CONFIGURED_ENGINES, typeRef);
    }
    return configuredEngines;
  }

  public Integer getTokenLifeTime() {
    if(tokenLifeTime == null) {
      tokenLifeTime = jsonContext.read(ConfigurationServiceConstants.TOKEN_LIFE_TIME);
    }
    return tokenLifeTime;
  }

  public String getElasticSearchHost() {
    if (elasticSearchHost == null) {
      elasticSearchHost = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_HOST);
    }
    return elasticSearchHost;
  }

  public Integer getElasticSearchPort() {
    if (elasticSearchPort == null) {
      elasticSearchPort = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_PORT);
    }
    return elasticSearchPort;
  }

  protected String getOptimizeIndex() {
    if (optimizeIndex == null) {
      optimizeIndex = jsonContext.read(ConfigurationServiceConstants.OPTIMIZE_INDEX);
    }
    return optimizeIndex;
  }

  public String getOptimizeIndex(String type) {
    String original = this.getOptimizeIndex() + "-" + type;
    return original.toLowerCase();
  }

  public String[] getOptimizeIndex(ArrayList<String> types) {
    String[] result = new String[types.size()];
    int i = 0;
    for (String type : types) {
      result[i] = this.getOptimizeIndex(type);
      i = i + 1;
    }
    return result;
  }

  public String getHistoricActivityInstanceEndpoint() {
    if (haiEndpoint == null) {
      haiEndpoint = jsonContext.read(ConfigurationServiceConstants.HAI_ENDPOINT);
    }
    return haiEndpoint;
  }

  public String getEventType() {
    if (eventType == null) {
      eventType = jsonContext.read(ConfigurationServiceConstants.EVENT_TYPE);
    }
    return eventType;
  }

  public String getUserValidationEndpoint() {
    if (userValidationEndpoint == null) {
      userValidationEndpoint = jsonContext.read(ConfigurationServiceConstants.USER_VALIDATION_ENDPOINT);
    }
    return userValidationEndpoint;
  }

  public String getProcessDefinitionType() {
    if (processDefinitionType == null) {
      processDefinitionType = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_TYPE);
    }
    return processDefinitionType;
  }

  public String getMetaDataType() {
    if (metaDataType == null) {
      metaDataType = jsonContext.read(ConfigurationServiceConstants.METADATA_TYPE);
    }
    return metaDataType;
  }

  public String getProcessDefinitionEndpoint() {
    if (processDefinitionEndpoint == null) {
      processDefinitionEndpoint = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_ENDPOINT);
    }
    return processDefinitionEndpoint;
  }

  public String getProcessDefinitionXmlType() {
    if (processDefinitionXmlType == null) {
      processDefinitionXmlType = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_TYPE);
    }
    return processDefinitionXmlType;
  }

  public String getAnalyzerName() {
    if (analyzerName == null) {
      analyzerName = jsonContext.read(ConfigurationServiceConstants.ANALYZER_NAME);
    }
    return analyzerName;
  }

  public String getTokenizer() {
    if (tokenizer == null) {
      tokenizer = jsonContext.read(ConfigurationServiceConstants.TOKENIZER);
    }
    return tokenizer;
  }

  public String getTokenFilter() {
    if (tokenFilter == null) {
      tokenFilter = jsonContext.read(ConfigurationServiceConstants.TOKEN_FILTER);
    }
    return tokenFilter;
  }

  public String getEngineDateFormat() {
    if (engineDateFormat == null) {
      engineDateFormat = jsonContext.read(ConfigurationServiceConstants.ENGINE_DATE_FORMAT);
    }
    return engineDateFormat;
  }

  public String getOptimizeDateFormat() {
    if (optimizeDateFormat == null) {
      optimizeDateFormat = jsonContext.read(ConfigurationServiceConstants.OPTIMIZE_DATE_FORMAT);
    }
    return optimizeDateFormat;
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec =
        jsonContext.read(ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class);
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public long getImportHandlerWait() {
    if (importHandlerWait == null) {
      importHandlerWait = jsonContext.read(ConfigurationServiceConstants.IMPORT_HANDLER_INTERVAL, Long.class);
    }
    return importHandlerWait;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff = jsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public Boolean isBackoffEnabled() {
    if (backoffEnabled == null) {
      backoffEnabled = jsonContext.read(ConfigurationServiceConstants.IS_BACK_OFF_ENABLED, Boolean.class);
    }
    return backoffEnabled;
  }

  public int getElasticsearchJobExecutorQueueSize() {
    if (elasticsearchJobExecutorQueueSize == null) {
      elasticsearchJobExecutorQueueSize = jsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_MAX_JOB_QUEUE_SIZE, Integer.class);
    }
    return elasticsearchJobExecutorQueueSize;
  }

  public int getElasticsearchJobExecutorThreadCount() {
    if (elasticsearchJobExecutorThreadCount == null) {
      elasticsearchJobExecutorThreadCount = jsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class);
    }
    return elasticsearchJobExecutorThreadCount;
  }

  public String getHistoricProcessInstanceEndpoint() {
    if (hpiEndpoint == null) {
      hpiEndpoint = jsonContext.read(ConfigurationServiceConstants.HPI_ENDPOINT);
    }
    return hpiEndpoint;
  }

  public int getElasticsearchScrollTimeout() {
    if (elasticsearchScrollTimeout == null) {
      elasticsearchScrollTimeout = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SCROLL_TIMEOUT, Integer.class);
    }
    return elasticsearchScrollTimeout;
  }

  public int getElasticsearchConnectionTimeout() {
    if (elasticsearchConnectionTimeout == null) {
      elasticsearchConnectionTimeout = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_TIMEOUT, Integer.class);
    }
    return elasticsearchConnectionTimeout;
  }

  public int getEngineConnectTimeout() {
    if (engineConnectTimeout == null) {
      engineConnectTimeout = jsonContext.read(ConfigurationServiceConstants.ENGINE_CONNECT_TIMEOUT, Integer.class);
    }
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    if (engineReadTimeout == null) {
      engineReadTimeout = jsonContext.read(ConfigurationServiceConstants.ENGINE_READ_TIMEOUT, Integer.class);
    }
    return engineReadTimeout;
  }

  public String getImportIndexType() {
    if (importIndexType == null) {
      importIndexType = jsonContext.read(ConfigurationServiceConstants.IMPORT_INDEX_TYPE);
    }
    return importIndexType;
  }

  public String getDurationHeatmapTargetValueType() {
    if (durationHeatmapTargetValueType == null) {
      durationHeatmapTargetValueType = jsonContext.read(ConfigurationServiceConstants.DURATION_HEATMAP_TARGET_VALUE_TYPE);
    }
    return durationHeatmapTargetValueType;
  }

  public String getVariableType() {
    if (variableType == null) {
      variableType = jsonContext.read(ConfigurationServiceConstants.VARIABLE_TYPE);
    }
    return variableType;
  }

  public String getProcessInstanceType() {
    if (processInstanceType == null) {
      processInstanceType = jsonContext.read(ConfigurationServiceConstants.PROCESS_INSTANCE_TYPE);
    }
    return processInstanceType;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    if (engineImportProcessInstanceMaxPageSize == null) {
      engineImportProcessInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    if (engineImportVariableInstanceMaxPageSize == null) {
      engineImportVariableInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public String getEsRefreshInterval() {
    if (esRefreshInterval == null) {
      esRefreshInterval = jsonContext.read(ConfigurationServiceConstants.ES_REFRESH_INTERVAL);
    }
    return esRefreshInterval;
  }

  public int getEsNumberOfReplicas() {
    if (esNumberOfReplicas == null) {
      esNumberOfReplicas = jsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_REPLICAS);
    }
    return esNumberOfReplicas;
  }

  public int getEsNumberOfShards() {
    if (esNumberOfShards == null) {
      esNumberOfShards = jsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_SHARDS);
    }
    return esNumberOfShards;
  }


  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE);
    }
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public String getProcessDefinitionXmlEndpoint() {
    if (processDefinitionXmlEndpoint == null) {
      processDefinitionXmlEndpoint = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_ENDPOINT);
    }
    return processDefinitionXmlEndpoint;
  }

  public String getLicenseType() {
    if (licenseType == null) {
      licenseType = jsonContext.read(ConfigurationServiceConstants.LICENSE_TYPE);
    }
    return licenseType;
  }

  public String getReportType() {
    if (reportType == null) {
      reportType = jsonContext.read(ConfigurationServiceConstants.REPORT_TYPE);
    }
    return reportType;
  }

  public String getDashboardType() {
    if (dashboardType == null) {
      dashboardType = jsonContext.read(ConfigurationServiceConstants.DASHBOARD_TYPE);
    }
    return dashboardType;
  }

  public long getSamplerInterval() {
    if (samplerInterval == null) {
      samplerInterval = jsonContext.read(ConfigurationServiceConstants.SAMPLER_INTERVAL, Long.class);
    }
    return samplerInterval;
  }

  public List<String> getVariableImportPluginBasePackages() {
    if (variableImportPluginBasePackages == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {};
      variableImportPluginBasePackages =
        jsonContext.read(ConfigurationServiceConstants.VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES, typeRef);
    }
    return variableImportPluginBasePackages;
  }

  public String getFinishedProcessInstanceIdTrackingType() {
    if (finishedPiIdTrackingType == null) {
      finishedPiIdTrackingType = jsonContext.read(ConfigurationServiceConstants.FINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE);
    }
    return finishedPiIdTrackingType;
  }

  public String getUnfinishedProcessInstanceIdTrackingType() {
    if (unfinishedPiIdTrackingType == null) {
      unfinishedPiIdTrackingType = jsonContext.read(ConfigurationServiceConstants.UNFINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE);
    }
    return unfinishedPiIdTrackingType;
  }

  public int getNumberOfRetriesOnConflict() {
    if (numberOfRetriesOnConflict == null) {
      numberOfRetriesOnConflict = jsonContext.read(ConfigurationServiceConstants.NUMBER_OF_RETRIES_ON_CONFLICT);
    }
    return numberOfRetriesOnConflict;
  }

  public long getEngineImportActivityInstanceMaxPageSize() {
    if (engineImportActivityInstanceMaxPageSize == null) {
      engineImportActivityInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE, Long.class);
    }
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword = jsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  public String getContainerKeystoreLocation() {
    if (containerKeystoreLocation == null) {
      containerKeystoreLocation = jsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_LOCATION);
    }
    return containerKeystoreLocation;
  }

  public int getContainerHttpsPort() {
    if (containerHttpsPort == null) {
      containerHttpsPort = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTPS_PORT);
    }
    return containerHttpsPort;
  }

  public int getContainerHttpPort() {
    if (containerHttpPort == null) {
      containerHttpPort = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTP_PORT);
    }
    return containerHttpPort;
  }

  public int getMaxStatusConnections() {
    if (maxStatusConnections == null) {
      maxStatusConnections = jsonContext.read(ConfigurationServiceConstants.CONTAINER_STATUS_MAX_CONNECTIONS);
    }
    return maxStatusConnections;
  }

  public Boolean getCheckMetadata() {
    if (checkMetadata == null) {
      checkMetadata = jsonContext.read(ConfigurationServiceConstants.CHECK_METADATA);
    }
    return checkMetadata;
  }

  public String getAlertType() {
    if (alertType == null) {
      alertType = jsonContext.read(ConfigurationServiceConstants.ALERT_TYPE);
    }
    return alertType;
  }

  public String getReportShareType() {
    if (reportShareType == null) {
      reportShareType = jsonContext.read(ConfigurationServiceConstants.REPORT_SHARE_TYPE);
    }
    return reportShareType;
  }

  public String getDashboardShareType() {
    if (dashboardShareType == null) {
      dashboardShareType = jsonContext.read(ConfigurationServiceConstants.DASHBOARD_SHARE_TYPE);
    }
    return dashboardShareType;
  }

  public String getProcessDefinitionXmlEndpoint(String processDefinitionId) {
    String processDefinitionXmlEndpoint =
        getProcessDefinitionEndpoint() + "/" + processDefinitionId + getProcessDefinitionXmlEndpoint();
    return processDefinitionXmlEndpoint;
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
   * should rely on {@link org.camunda.optimize.service.util.configuration.ConfigurationService#getEngineRestApiEndpointOfCustomEngine(java.lang.String)}
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
      quartzProperties.put("org.quartz.jobStore.class", jsonContext.read(ConfigurationServiceConstants.QUARTZ_JOB_STORE_CLASS));
    }
    return quartzProperties;
  }

  public String getAlertEmailUsername() {
    if (alertEmailUsername == null) {
      alertEmailUsername = jsonContext.read(ConfigurationServiceConstants.EMAIL_USERNAME);
    }
    return alertEmailUsername;
  }

  public String getAlertEmailPassword() {
    if (alertEmailPassword == null) {
      alertEmailPassword = jsonContext.read(ConfigurationServiceConstants.EMAIL_PASSWORD);
    }
    return alertEmailPassword;
  }

  public boolean isEmailEnabled() {
    if (emailsEnabled == null) {
      emailsEnabled = jsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED);
    }
    return emailsEnabled;
  }

  public String getAlertEmailAddress() {
    if (alertEmailAddress == null) {
      alertEmailAddress = jsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return alertEmailAddress;
  }

  public String getAlertEmailHostname() {
    if (alertEmailHostname == null) {
      alertEmailHostname = jsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return alertEmailHostname;
  }

  public Integer getAlertEmailPort() {
    if (alertEmailPort == null) {
      alertEmailPort = jsonContext.read(ConfigurationServiceConstants.EMAIL_PORT);
    }
    return alertEmailPort;
  }

  public String getAlertEmailProtocol() {
    if (alertEmailProtocol == null) {
      alertEmailProtocol = jsonContext.read(ConfigurationServiceConstants.EMAIL_PROTOCOL);
    }
    return alertEmailProtocol;
  }

  public Integer getExportCsvLimit() {
    if (exportCsvLimit == null) {
      exportCsvLimit = jsonContext.read(ConfigurationServiceConstants.EXPORT_CSV_LIMIT);
    }
    return exportCsvLimit;
  }

  public Integer getExportCsvOffset() {
    if (exportCsvOffset == null) {
      exportCsvOffset = jsonContext.read(ConfigurationServiceConstants.EXPORT_CSV_OFFSET);
    }
    return exportCsvOffset;
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

  public void setConfiguredEngines(Map<String, EngineConfiguration> configuredEngines) {
    this.configuredEngines = configuredEngines;
  }

  public static String getEnginesField() {
    return ENGINES_FIELD;
  }

  public static String getEngineRestPath() {
    return ENGINE_REST_PATH;
  }

  public Logger getLogger() {
    return logger;
  }

  public static String[] getDefaultLocations() {
    return DEFAULT_LOCATIONS;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HashMap getDefaults() {
    return defaults;
  }

  public void setDefaults(HashMap defaults) {
    this.defaults = defaults;
  }

  public ReadContext getJsonContext() {
    return jsonContext;
  }

  public void setJsonContext(ReadContext jsonContext) {
    this.jsonContext = jsonContext;
  }

  public void setTokenLifeTime(Integer tokenLifeTime) {
    this.tokenLifeTime = tokenLifeTime;
  }

  public void setElasticSearchHost(String elasticSearchHost) {
    this.elasticSearchHost = elasticSearchHost;
  }

  public void setElasticSearchPort(Integer elasticSearchPort) {
    this.elasticSearchPort = elasticSearchPort;
  }

  public void setOptimizeIndex(String optimizeIndex) {
    this.optimizeIndex = optimizeIndex;
  }

  public void setHaiEndpoint(String haiEndpoint) {
    this.haiEndpoint = haiEndpoint;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public void setUserValidationEndpoint(String userValidationEndpoint) {
    this.userValidationEndpoint = userValidationEndpoint;
  }

  public void setProcessDefinitionType(String processDefinitionType) {
    this.processDefinitionType = processDefinitionType;
  }

  public void setProcessDefinitionEndpoint(String processDefinitionEndpoint) {
    this.processDefinitionEndpoint = processDefinitionEndpoint;
  }

  public void setProcessDefinitionXmlType(String processDefinitionXmlType) {
    this.processDefinitionXmlType = processDefinitionXmlType;
  }

  public void setImportIndexAutoStorageIntervalInSec(Integer importIndexAutoStorageIntervalInSec) {
    this.importIndexAutoStorageIntervalInSec = importIndexAutoStorageIntervalInSec;
  }

  public void setAnalyzerName(String analyzerName) {
    this.analyzerName = analyzerName;
  }

  public void setTokenizer(String tokenizer) {
    this.tokenizer = tokenizer;
  }

  public void setTokenFilter(String tokenFilter) {
    this.tokenFilter = tokenFilter;
  }

  public void setEngineDateFormat(String engineDateFormat) {
    this.engineDateFormat = engineDateFormat;
  }

  public void setOptimizeDateFormat(String optimizeDateFormat) {
    this.optimizeDateFormat = optimizeDateFormat;
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

  public void setHpiEndpoint(String hpiEndpoint) {
    this.hpiEndpoint = hpiEndpoint;
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

  public void setImportIndexType(String importIndexType) {
    this.importIndexType = importIndexType;
  }

  public void setDurationHeatmapTargetValueType(String durationHeatmapTargetValueType) {
    this.durationHeatmapTargetValueType = durationHeatmapTargetValueType;
  }

  public void setVariableType(String variableType) {
    this.variableType = variableType;
  }

  public void setProcessInstanceType(String processInstanceType) {
    this.processInstanceType = processInstanceType;
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

  public void setProcessDefinitionXmlEndpoint(String processDefinitionXmlEndpoint) {
    this.processDefinitionXmlEndpoint = processDefinitionXmlEndpoint;
  }

  public void setLicenseType(String licenseType) {
    this.licenseType = licenseType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public void setDashboardType(String dashboardType) {
    this.dashboardType = dashboardType;
  }

  public void setSamplerInterval(Long samplerInterval) {
    this.samplerInterval = samplerInterval;
  }

  public void setFinishedPiIdTrackingType(String finishedPiIdTrackingType) {
    this.finishedPiIdTrackingType = finishedPiIdTrackingType;
  }

  public void setNumberOfRetriesOnConflict(Integer numberOfRetriesOnConflict) {
    this.numberOfRetriesOnConflict = numberOfRetriesOnConflict;
  }

  public void setEngineImportActivityInstanceMaxPageSize(Long engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
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

  public void setBackoffEnabled(Boolean backoffEnabled) {
    this.backoffEnabled = backoffEnabled;
  }

  public void setAlertType(String alertType) {
    this.alertType = alertType;
  }

  public void setAlertEmailUsername(String alertEmailUsername) {
    this.alertEmailUsername = alertEmailUsername;
  }

  public void setEmailsEnabled(Boolean emailsEnabled) {
    this.emailsEnabled = emailsEnabled;
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

}
