/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CombinedReportHandlingIT extends AbstractIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String TEST_REPORT_NAME = "My foo report";
  private static final String USER_TASK_ID = "userTask";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCombinedReport();

    // then
    GetRequest getRequest = new GetRequest(COMBINED_REPORT_INDEX_NAME).id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    assertThat(getResponse.isExists()).isTrue();
    CombinedReportDefinitionDto definitionDto = elasticSearchIntegrationTestExtension.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), CombinedReportDefinitionDto.class);
    assertThat(definitionDto.getData()).isNotNull();
    CombinedReportDataDto data = definitionDto.getData();

    assertThat(data.getConfiguration()).isNotNull();
    assertThat(data.getConfiguration()).isEqualTo(new CombinedReportConfigurationDto());
    assertThat(definitionDto.getData().getReportIds()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("getUncombinableSingleReports")
  public void combineUncombinableSingleReports(List<SingleProcessReportDefinitionDto> singleReports) {
    //given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(report -> new CombinedReportItemDto(createNewSingleReport(report)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    //when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute();

    //then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("getCombinableSingleReports")
  public void combineCombinableSingleReports(List<SingleProcessReportDefinitionDto> singleReports) {
    //given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(report -> new CombinedReportItemDto(createNewSingleReport(report)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    //when
    IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    AuthorizedCombinedReportEvaluationResultDto<SingleReportResultDto> result =
      reportClient.evaluateCombinedReportById(response.getId());

    assertThat(result.getReportDefinition().getData().getReports()).containsExactlyInAnyOrderElementsOf(reportIds);
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> getCombinableSingleReports() {
    //different procDefs
    SingleProcessReportDefinitionDto procDefKeyReport = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto procDefKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefKeyReport.setData(procDefKeyReportData);

    SingleProcessReportDefinitionDto procDefAnotherKeyReport = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto procDefAnotherKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    procDefAnotherKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefAnotherKeyReport.setData(procDefAnotherKeyReportData);

    //byStartDate/byEndDate
    SingleProcessReportDefinitionDto byEndDate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto byEndDateData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    byEndDateData.setVisualization(ProcessVisualization.BAR);
    byEndDate.setData(byEndDateData);

    //userTaskDuration/flowNodeDuration
    SingleProcessReportDefinitionDto userTaskDuration = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto userTaskDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    userTaskDurationData.setVisualization(ProcessVisualization.BAR);
    userTaskDuration.setData(userTaskDurationData);

    SingleProcessReportDefinitionDto flowNodeDuration = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto flowNodeDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    flowNodeDurationData.setVisualization(ProcessVisualization.BAR);
    flowNodeDuration.setData(flowNodeDurationData);

    // groupBy number variable reports with same bucket size
    SingleProcessReportDefinitionDto groupByNumberVar1 = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByNumberVar1Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar1Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar1Data.getConfiguration().getCustomNumberBucket().setActive(true);
    groupByNumberVar1Data.getConfiguration().getCustomNumberBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar1Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar1.setData(groupByNumberVar1Data);

    SingleProcessReportDefinitionDto groupByNumberVar2 = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByNumberVar2Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar2Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar2Data.getConfiguration().getCustomNumberBucket().setActive(true);
    groupByNumberVar2Data.getConfiguration().getCustomNumberBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar2Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar2.setData(groupByNumberVar2Data);

    return Stream.of(
      Arrays.asList(procDefKeyReport, procDefAnotherKeyReport),
      Arrays.asList(byEndDate, procDefKeyReport),
      Arrays.asList(userTaskDuration, flowNodeDuration),
      Arrays.asList(groupByNumberVar1, groupByNumberVar2)
    );
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> getUncombinableSingleReports() {
    // uncombinable visualization
    SingleProcessReportDefinitionDto PICount_startDateYear_bar = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PICount_startDateYear_barData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    PICount_startDateYear_barData.setVisualization(ProcessVisualization.BAR);
    PICount_startDateYear_bar.setData(PICount_startDateYear_barData);

    SingleProcessReportDefinitionDto PICount_startDateYear_line = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PICount_startDateYear_lineData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setDateInterval(GroupByDateUnit.YEAR)
      .build();

    PICount_startDateYear_lineData.setVisualization(ProcessVisualization.LINE);
    PICount_startDateYear_line.setData(PICount_startDateYear_lineData);

    //uncombinable groupBy
    ProcessReportDataDto PICount_byVariable_barData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVariableName("var")
      .setVariableType(VariableType.BOOLEAN)
      .build();
    PICount_byVariable_barData.setVisualization(ProcessVisualization.BAR);
    SingleProcessReportDefinitionDto PICount_byVariable_bar = new SingleProcessReportDefinitionDto();
    PICount_byVariable_bar.setData(PICount_byVariable_barData);

    //uncombinable view
    SingleProcessReportDefinitionDto PIDuration_startDateYear_bar = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto PIDuration_startDateYear_barData = new ProcessReportDataBuilderHelper()
      .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
      .viewProperty(ProcessViewProperty.DURATION)
      .groupByType(ProcessGroupByType.START_DATE)
      .dateInterval(GroupByDateUnit.YEAR)
      .processDefinitionKey("key")
      .processDefinitionVersions(Collections.singletonList("1"))
      .build();

    PIDuration_startDateYear_barData.setVisualization(ProcessVisualization.BAR);
    PIDuration_startDateYear_bar.setData(PIDuration_startDateYear_barData);

    // groupBy number variable reports with different bucket size
    SingleProcessReportDefinitionDto groupByNumberVar1 = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByNumberVar1Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar1Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar1Data.getConfiguration().getCustomNumberBucket().setActive(true);
    groupByNumberVar1Data.getConfiguration().getCustomNumberBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar1Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar1.setData(groupByNumberVar1Data);

    SingleProcessReportDefinitionDto groupByNumberVar2 = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByNumberVar2Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar2Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar1Data.getConfiguration().getCustomNumberBucket().setActive(true);
    groupByNumberVar2Data.getConfiguration().getCustomNumberBucket().setBucketSize(10.0);
    ((VariableGroupByValueDto) groupByNumberVar2Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar2.setData(groupByNumberVar2Data);

    return Stream.of(
      Arrays.asList(PICount_startDateYear_bar, PICount_startDateYear_line),
      Arrays.asList(PICount_byVariable_bar, PICount_startDateYear_bar),
      Arrays.asList(PICount_startDateYear_bar, PIDuration_startDateYear_bar),
      Arrays.asList(groupByNumberVar1, groupByNumberVar2)
    );
  }

  @Test
  public void getSingleAndCombinedReport() {
    // given
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionDto());
    String combinedReportId = createNewCombinedReport();

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet).hasSize(2);
    assertThat(resultSet.contains(singleReportId)).isTrue();
    assertThat(resultSet.contains(combinedReportId)).isTrue();
  }

  @Test
  public void updateCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    importAllEngineEntitiesFromScratch();

    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = createNewCombinedReport();
    String singleReportId = createNewSingleNumberReport(engineDto);
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportId));
    report.getData().getConfiguration().setXLabel("FooXLabel");
    report.setId(shouldNotBeUpdatedString);
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner(shouldNotBeUpdatedString);

    // when
    updateReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).hasSize(2);
    CombinedReportDefinitionDto newReport = (CombinedReportDefinitionDto) reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionDto).findFirst().get();
    assertThat(newReport.getData().getReportIds()).isNotEmpty();
    assertThat(newReport.getData().getReportIds().get(0)).isEqualTo(singleReportId);
    assertThat(newReport.getData().getConfiguration().getXLabel()).isEqualTo("FooXLabel");
    assertThat(newReport.getData().getVisualization()).isEqualTo(ProcessVisualization.NUMBER);
    assertThat(newReport.getId()).isEqualTo(id);
    assertThat(newReport.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getName()).isEqualTo("MyReport");
    assertThat(newReport.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  private Stream<Function<CombinedReportUpdateData, Response>> reportUpdateScenarios() {
    return Stream.of(
      data -> {
        String combinedReportId = createNewCombinedReportInCollection(data.getCollectionId());
        return addSingleReportToCombinedReport(combinedReportId, data.getSingleReportId());
      },
      data -> {
        CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
        combinedReportData.setReports(Collections.singletonList(new CombinedReportItemDto(data.getSingleReportId())));
        CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
        combinedReport.setData(combinedReportData);
        combinedReport.setCollectionId(data.getCollectionId());
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateCombinedReportRequest(combinedReport)
          .execute();
      }
    );
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCanBeAddedToSameCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus()).isIn(
      Arrays.asList(Response.Status.OK.getStatusCode(), Response.Status.NO_CONTENT.getStatusCode()
      ));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToOtherCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId2);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId1));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToPrivateCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, null));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportReportCannotBeAddedToCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(null);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportAddingOtherUsersPrivateReportFails(Function<CombinedReportUpdateData,
    Response> scenario) {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String reportId = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(reportId, null));


    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void addUncombinableReportThrowsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String numberReportId = createNewSingleNumberReport(engineDto);
    String rawReportId = createNewSingleRawReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport();
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(createCombinedReportData(numberReportId, rawReportId));
    ErrorResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReport, true)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(response.getErrorCode()).isEqualTo("reportsNotCombinable");
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportId));
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    updateReport(reportId, report);

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = reportClient.evaluateCombinedReportById(
      reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    assertThat(result.getReportDefinition().getName()).isEqualTo("name");
    assertThat(result.getReportDefinition().getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(result.getReportDefinition().getCreated().truncatedTo(ChronoUnit.DAYS))
      .isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
    assertThat(result.getReportDefinition().getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(result.getReportDefinition().getLastModified().truncatedTo(ChronoUnit.DAYS))
      .isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
    assertThat(result.getResult().getData()).isNotNull();
    assertThat(result.getReportDefinition().getData().getReportIds()).hasSize(1);
    assertThat(result.getReportDefinition().getData().getReportIds().get(0)).isEqualTo(singleReportId);
    assertThat(result.getReportDefinition().getData().getConfiguration())
      .isEqualTo(new CombinedReportConfigurationDto());
  }

  @Test
  public void deleteCombinedReport() {
    // given
    String reportId = createNewCombinedReport();

    // when
    deleteReport(reportId);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).isEmpty();
  }

  @Test
  public void canSaveAndEvaluateCombinedReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = reportClient.evaluateCombinedReportById(
      reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount).hasSize(3);
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2).hasSize(3);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFiltersAppliedToContainedReports() {
    // given
    ProcessInstanceEngineDto runningInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    ProcessInstanceEngineDto completedInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstanceEngineDto.getId());
    String runningInstanceReportId = createNewSingleMapReport(runningInstanceEngineDto);
    String completedInstanceReportId = createNewSingleMapReport(completedInstanceEngineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(runningInstanceReportId, completedInstanceReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data for their single instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(runningInstanceReportId).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(result.getResult().getData().get(completedInstanceReportId).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();

    // when completed instance filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .completedInstancesOnly().add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then only the running instance report returns data for its instance
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(runningInstanceReportId).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
    assertThat(filteredResult.getResult().getData().get(completedInstanceReportId).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFiltersAppliedToContainedReportsWithExistingReportFilters() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    AdditionalProcessReportEvaluationFilterDto singleReportFilterDto = new AdditionalProcessReportEvaluationFilterDto();
    singleReportFilterDto.setFilter(ProcessFilterBuilder.filter()
                                      .fixedEndDate()
                                      .end(OffsetDateTime.now().plusDays(1))
                                      .add()
                                      .buildList());
    String singleReportIdA = createNewSingleMapReportWithFilter(engineDto, singleReportFilterDto);
    String singleReportIdB = createNewSingleMapReportWithFilter(engineDto, singleReportFilterDto);
    importAllEngineEntitiesFromScratch();

    // when no additional filters are applied
    String combinedReportId = createNewCombinedReport(singleReportIdA, singleReportIdB);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data with no null values
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .doesNotContainNull()
      );

    // when future start date filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .fixedStartDate().start(OffsetDateTime.now().plusDays(1)).add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the data values are now null
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .containsOnlyNulls()
      );
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_allDataFilteredOutWithMultipleFilters() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdA = createNewSingleMapReport(engineDto);
    String singleReportIdB = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(singleReportIdA, singleReportIdB);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data with no null values
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .doesNotContainNull()
      );

    // when running and completed instance filters applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .runningInstancesOnly().add()
                          .completedInstancesOnly().add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the data values are now null
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .containsOnlyNulls()
      );
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableNotPresentForEitherReport() {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    String report1 = createNewSingleMapReport(processInstanceEngineDto);
    String report2 = createNewSingleMapReport(processInstanceEngineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(report1, report2);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(report1).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(result.getResult().getData().get(report2).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);

    // when variable filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter("someVarName", "varValue"));
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the filter is ignored as the filter name/type is not known
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableExistsInOneReport() {
    // given
    final String varName = "var1";
    ProcessInstanceEngineDto variableInstance = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val1"
    ));
    ProcessInstanceEngineDto noVariableInstance = deployAndStartSimpleUserTaskProcess();
    String variableReport = createNewSingleMapReport(variableInstance);
    String noVariableReport = createNewSingleMapReport(noVariableInstance);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(variableReport, noVariableReport);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(variableReport).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(result.getResult().getData().get(noVariableReport).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);

    // when variable filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "someOtherValue"));
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the filter is applied to the report where it exists and ignored in the no variable report
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(variableReport).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
    assertThat(filteredResult.getResult().getData().get(noVariableReport).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableExistsInBothReports() {
    // given
    final String varName = "var1";
    ProcessInstanceEngineDto instance1 = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val1"
    ));
    ProcessInstanceEngineDto instance2 = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val2"
    ));
    String report1 = createNewSingleMapReport(instance1);
    String report2 = createNewSingleMapReport(instance2);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(report1, report2);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(report1).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(result.getResult().getData().get(report2).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);

    // when variable filter applied with other value
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "someOtherValue"));
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> filteredResult =
      reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the filter is applied to both reports correctly
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();

    // when variable filter applied that matches value form single report
    filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "val1"));
    filteredResult = reportClient.evaluateCombinedReportByIdWithFilters(combinedReportId, filterDto);

    // then the filter is applied to both reports correctly
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1., 1., null);
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationReportsOfDifferentDurationViewProperties() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(totalDurationReportId, idleDurationReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = reportClient.evaluateCombinedReportById(
      reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(totalDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount1).hasSize(1);
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(idleDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount2).hasSize(1);
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationAndProcessDurationReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String userTaskTotalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String flowNodeDurationReportId = createNewSingleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(userTaskTotalDurationReportId, flowNodeDurationReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = reportClient.evaluateCombinedReportById(
      reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(userTaskTotalDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount1).hasSize(1);
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(flowNodeDurationReportId)
      .getResult()
      .getData();
    assertThat(userTaskCount2).hasSize(3);
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithStartAndEndDateGroupedReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    final AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto>
      result = reportClient.evaluateCombinedReportById(combinedReportId);

    // then
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap)
      .isNotNull()
      .hasSize(2);
    assertThat(resultMap.keySet()).containsExactlyInAnyOrder(singleReportId1, singleReportId2);

    final ReportMapResultDto result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getData();
    assertThat(resultData1).isNotNull().hasSize(1);

    final ReportMapResultDto result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void reportsThatCantBeEvaluatedAreIgnored() {
    // given
    deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionDto());
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = reportClient.evaluateCombinedReportById(
      reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).isEmpty();
  }

  @Test
  public void deletedSingleReportsAreRemovedFromCombinedReportWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdToDelete = createNewSingleMapReport(engineDto);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToDelete, remainingSingleReportId);
    deleteReport(singleReportIdToDelete, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet).hasSize(2);
    assertThat(resultSet.contains(remainingSingleReportId)).isTrue();
    assertThat(resultSet.contains(combinedReportId)).isTrue();
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(report -> report instanceof CombinedReportDefinitionDto)
      .map(report -> (CombinedReportDefinitionDto) report)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithVisualizeAsChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.setVisualization(ProcessVisualization.TABLE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .contains(combinedReportId);
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionDto)
      .map(reportDto -> (CombinedReportDefinitionDto) reportDto)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithGroupByChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getGroupBy().setType(ProcessGroupByType.START_DATE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .contains(combinedReportId);
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionDto)
      .map(reportDto -> (CombinedReportDefinitionDto) reportDto)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithViewChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .contains(combinedReportId);
    Optional<CombinedReportDefinitionDto> combinedReport = reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionDto)
      .map(reportDto -> (CombinedReportDefinitionDto) reportDto)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getData();

    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getData();
    assertThat(flowNodeToCount).hasSize(3);
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getData();
    assertThat(flowNodeToCount2).hasSize(3);
  }

  @Test
  public void evaluationResultContainsSingleResultMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(1);
    AuthorizedEvaluationResultDto<ReportMapResultDto, SingleProcessReportDefinitionDto> mapResult =
      resultMap.get(singleReportId);
    assertThat(mapResult.getReportDefinition().getName()).isEqualTo(TEST_REPORT_NAME);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleNumberReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationNumberReport(engineDto);
    String singleReportId2 = createNewSingleDurationNumberReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationMapReport(engineDto);
    String singleReportId2 = createNewSingleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String totalDurationReportId2 = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, totalDurationReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, totalDurationReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationAndUserTaskIdleDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, idleDurationReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReportAndUserTaskTotalDurationMapReport() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, idleDurationReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithGroupedByProcessInstanceStartAndEndDateReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, GroupByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, GroupByDateUnit.DAY);

    importAllEngineEntitiesFromScratch();

    // when
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);

    final ReportMapResultDto result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getData();
    assertThat(resultData1)
      .isNotNull()
      .hasSize(1);

    final ReportMapResultDto result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithGroupedByUserTaskStartAndEndDateReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeUserTaskStartDate(engineDto.getId(), USER_TASK_ID, now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto groupedByEndDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE)
      .build();
    String groupedByEndDateReportId = createNewSingleMapReport(groupedByEndDateReportData);
    final ProcessReportDataDto groupedByStartDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE)
      .build();
    String groupedByStartDateReportId = createNewSingleMapReport(groupedByStartDateReportData);

    // when
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(groupedByEndDateReportId, groupedByStartDateReportId));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.keySet()).contains(groupedByEndDateReportId, groupedByStartDateReportId);

    final ReportMapResultDto result1 = resultMap.get(groupedByEndDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getData();
    assertThat(resultData1)
      .isNotNull()
      .hasSize(1);

    final ReportMapResultDto result2 = resultMap.get(groupedByStartDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberAndMapReport_firstWins() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<NumberResultDto> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(1);
    assertThat(resultMap.keySet()).contains(singleReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithRawReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleRawReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getData();
    assertThat(resultMap)
      .hasSize(1)
      .containsKey(singleReportId2);
  }

  @Test
  public void cantEvaluateCombinedReportWithCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String combinedReportId = createNewCombinedReport();
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    Response response =
      evaluateUnsavedCombinedReportAndReturnResponse(createCombinedReportData(combinedReportId, singleReportId2));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void combinedReportWithHyperMapReportCanBeEvaluated() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    createNewCombinedReport();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
    String singleReportId = createNewSingleMapReport(reportData);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap =
      result.getData();
    assertThat(resultMap).isEmpty();
  }

  private List<ProcessFilterDto<?>> buildStringVariableFilter(final String varName, final String varValue) {
    return ProcessFilterBuilder.filter()
      .variable()
      .name(varName)
      .stringType()
      .operator(IN)
      .values(Collections.singletonList(varValue))
      .add()
      .buildList();
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    return createNewSingleMapReportWithFilter(engineDto, null);
  }

  private String createNewSingleMapReportWithFilter(final ProcessInstanceEngineDto engineDto,
                                                    final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final TemplatedProcessReportDataBuilder templatedProcessReportDataBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE);
    Optional.ofNullable(filterDto)
      .ifPresent(filters -> templatedProcessReportDataBuilder.setFilter(filters.getFilter()));
    return createNewSingleMapReport(templatedProcessReportDataBuilder.build());
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(durationReportData);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskTotalDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskIdleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleNumberReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReportGroupByEndDate(ProcessInstanceEngineDto engineDto,
                                                     GroupByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByEndDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(groupByDateUnit)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
    return createNewSingleMapReport(reportDataByEndDate);
  }

  private String createNewSingleReportGroupByStartDate(ProcessInstanceEngineDto engineDto,
                                                       GroupByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByStartDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    return createNewSingleMapReport(reportDataByStartDate);
  }


  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportIds));
    return createNewCombinedReport(report);
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto report) {
    String reportId = createNewCombinedReportInCollection(null);
    updateReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReportInCollection(String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    return deployAndStartSimpleUserTaskProcessWithVariables(Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent("startEvent")
      .userTask(USER_TASK_ID)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void deleteReport(String reportId) {
    deleteReport(reportId, null);
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport, Boolean force) {
    Response response = getUpdateSingleProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private void updateReport(String id, CombinedReportDefinitionDto updatedReport) {
    updateReport(id, updatedReport, null);
  }

  private void updateReport(String id, CombinedReportDefinitionDto updatedReport, Boolean force) {
    Response response = getUpdateCombinedProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private Response getUpdateSingleProcessReportResponse(String id, SingleProcessReportDefinitionDto updatedReport,
                                                        Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response getUpdateCombinedProcessReportResponse(String id, CombinedReportDefinitionDto updatedReport,
                                                          Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return getAllPrivateReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllPrivateReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .addQueryParams(queryParams)
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private Response addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData().getReports().add(new CombinedReportItemDto(reportId, "red"));
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class CombinedReportUpdateData {
    String singleReportId;
    String collectionId;
  }
}
