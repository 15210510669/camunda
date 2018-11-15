package org.camunda.optimize.reimport.preparation;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilderHelper;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ForceReimportIT {

  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineRule = new EngineIntegrationRule("reimport-preparation.properties");

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void forceReimport() throws
                              IOException,
                              URISyntaxException {

    //given
    ProcessDefinitionEngineDto processDefinitionEngineDto = deployAndStartSimpleServiceTask();
    String reportId = createAndStoreNumberReport(processDefinitionEngineDto);
    AlertCreationDto alert = setupBasicAlert(reportId);
    embeddedOptimizeRule
            .getRequestExecutor().buildCreateAlertRequest(alert).execute();
    embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateDashboardRequest()
            .execute(IdDto.class, 200);
    addLicense();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<SingleReportDefinitionDto> reports = getAllReports();
    List<DashboardDefinitionDto> dashboards = getAllDashboards();
    List<AlertDefinitionDto> alerts = getAllAlerts();

    // then
    assertThat(licenseExists(), is(true));
    assertThat(reports.size(), is(1));
    assertThat(dashboards.size(), is(1));
    assertThat(alerts.size(), is(1));
    assertThat(hasEngineData(), is(true));

    // when
    forceReimportOfEngineData();

    reports = getAllReports();
    dashboards = getAllDashboards();
    alerts = getAllAlerts();

    // then
    assertThat(licenseExists(), is(true));
    assertThat(reports.size(), is(1));
    assertThat(dashboards.size(), is(1));
    assertThat(alerts.size(), is(1));
    assertThat(hasEngineData(), is(false));
  }

  private boolean hasEngineData() {
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();

    List<String> types = new ArrayList<>();
    types.add(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);
    types.add(configurationService.getImportIndexType());
    types.add(configurationService.getProcessDefinitionType());
    types.add(configurationService.getProcessInstanceType());

    List<String> indexNames = types
      .stream()
      .map(OptimizeIndexNameHelper::getOptimizeIndexAliasForType)
      .collect(Collectors.toList());

    SearchResponse response = elasticSearchRule.getClient()
      .prepareSearch(indexNames.toArray(new String[0]))
      .setTypes(types.toArray(new String[0]))
      .setQuery(QueryBuilders.matchAllQuery())
      .get();

    return response.getHits().getTotalHits() > 0L;
  }

  private boolean licenseExists() {
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    GetResponse response = elasticSearchRule.getClient().prepareGet(
      getOptimizeIndexAliasForType(configurationService.getLicenseType()),
      configurationService.getLicenseType(),
      configurationService.getLicenseType()
    )
      .get();
    return response.isExists();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String readFileToString() throws IOException, URISyntaxException {
    return new String(
      Files.readAllBytes(Paths.get(getClass().getResource("/license/ValidTestLicense.txt").toURI())),
      StandardCharsets.UTF_8
    );
  }

  private void addLicense() throws IOException, URISyntaxException {
    String license = readFileToString();

    embeddedOptimizeRule.getRequestExecutor()
            .buildValidateAndStoreLicenseRequest(license)
            .execute();
  }

  private AlertCreationDto setupBasicAlert(String reportId) {
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    return createSimpleAlert(reportId);
  }

  private String createNewReportHelper() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private String createAndStoreNumberReport(ProcessDefinitionEngineDto processDefinition) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getReportDefinitionDto(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
            .execute();
    assertThat(response.getStatus(), is(204));
  }

  protected SingleReportDefinitionDto getReportDefinitionDto(ProcessDefinitionEngineDto processDefinition) {
    return getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  private SingleReportDefinitionDto getReportDefinitionDto(String processDefinitionKey,
                                                           String processDefinitionVersion) {
    SingleReportDataDto reportData =
      ReportDataBuilderHelper.createPiFrequencyCountGroupedByNoneAsNumber(processDefinitionKey, processDefinitionVersion);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    return report;
  }

  private AlertCreationDto createSimpleAlert(String reportId) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    return getAllDashboardsWithQueryParam(new HashMap<>());
  }

  private List<DashboardDefinitionDto> getAllDashboardsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .addQueryParams(queryParams)
            .buildGetAllDashboardsRequest()
            .executeAndReturnList(DashboardDefinitionDto.class, 200);
  }


  private void forceReimportOfEngineData() throws IOException {
    ReimportPreparation.main(new String[]{});
  }

  private List<SingleReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<SingleReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllReportsRequest()
            .addQueryParams(queryParams)
            .executeAndReturnList(SingleReportDefinitionDto.class, 200);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();

    ProcessDefinitionEngineDto processDefinitionEngineDto =
      engineRule.deployProcessAndGetProcessDefinition(processModel);
    engineRule.startProcessInstance(processDefinitionEngineDto.getId(), variables);
    return processDefinitionEngineDto;
  }

}
