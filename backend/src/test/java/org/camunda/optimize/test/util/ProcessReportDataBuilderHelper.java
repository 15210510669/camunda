package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessParametersDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskIdleDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskTotalDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskWorkDurationView;


public class ProcessReportDataBuilderHelper {

  public static ProcessReportDataDto createProcessReportDataViewRawAsTable(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {
    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      new ProcessViewDto(ProcessViewProperty.RAW_DATA),
      createGroupByNone()
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateReport(
    String processDefinitionKey,
    String processDefinitionVersion,
    GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateWithProcessPartReport(
    String processDefinitionKey,
    String processDefinitionVersion,
    GroupByDateUnit dateInterval,
    String startFlowNodeId,
    String endFlowNodeId
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto,
      processPartDto
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByStartDate(
    String processDefinitionKey,
    String processDefinitionVersion,
    GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    ProcessReportDataDto reportData = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );

    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    String processDefinitionVersion,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto
  ) {
    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      visualization,
      viewDto,
      groupByDto,
      null
    );
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    String processDefinitionVersion,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto,
    ProcessPartDto processPartDto
  ) {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(processDefinitionKey);
    reportData.setProcessDefinitionVersion(processDefinitionVersion);
    reportData.setVisualization(visualization);
    reportData.setView(viewDto);
    reportData.setGroupBy(groupByDto);
    reportData.setParameters(new ProcessParametersDto(processPartDto));
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNode(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {

    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);


    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByVariable(
    String processDefinitionKey,
    String processDefinitionVersion,
    String variableName,
    VariableType variableType
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariable(
    String processDefinitionKey,
    String processDefinitionVersion,
    String variableName,
    VariableType variableType
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableWithProcessPart(
    String processDefinitionKey,
    String processDefinitionVersion,
    String variableName,
    VariableType variableType,
    String startFlowNodeId,
    String endFlowNodeId
  ) {
    ProcessReportDataDto reportData =
      createProcessInstanceDurationGroupByVariable(
        processDefinitionKey,
        processDefinitionVersion,
        variableName,
        variableType
      );
    reportData.getParameters().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeNumber(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {

    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);


    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createFlowNodeDurationGroupByFlowNodeTableReport(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {
    ProcessViewDto view = ProcessViewDtoCreator.createFlowNodeDurationView();

    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createFlowNodeDurationGroupByFlowNodeHeatmapReport(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {
    ProcessViewDto view = ProcessViewDtoCreator.createFlowNodeDurationView();

    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskTotalDurationMapGroupByUserTaskReport(
    final String processDefinitionKey,
    final String processDefinitionVersion
  ) {
    final ProcessViewDto view = createUserTaskTotalDurationView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByUserTaskReport(
    final String processDefinitionKey,
    final String processDefinitionVersion
  ) {
    final ProcessViewDto view = createUserTaskIdleDurationView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskWorkDurationMapGroupByUserTaskReport(
    final String processDefinitionKey,
    final String processDefinitionVersion) {
    final ProcessViewDto view = createUserTaskWorkDurationView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNone(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneWithProcessPart(
    String processDefinitionKey,
    String processDefinitionVersion,
    String startFlowNodeId,
    String endFlowNodeId
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto,
      processPartDto
    );
  }

  public static ProcessReportDataDto createPiFrequencyCountGroupedByNone(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createPiFrequencyCountGroupedByNoneAsNumber(String processDefinitionKey,
                                                                                 String processDefinitionVersion) {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();

    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      //does not really affect backend, since command object is instantiated based on
      //group by criterion
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static CombinedReportDataDto createCombinedReport(String... reportIds) {
    CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReports(
      Arrays.stream(reportIds).map(CombinedReportItemDto::new).collect(Collectors.toList())
    );
    return combinedReportDataDto;
  }

  private static ProcessPartDto createProcessPart(String start, String end) {
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart(start);
    processPartDto.setEnd(end);
    return processPartDto;
  }

}
