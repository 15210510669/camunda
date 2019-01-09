package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionEvaluationDateFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByFixedEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(OffsetDateTime.now(), null)));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(0L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));
  }

  @Test
  public void resultFilterByFixedEvaluationDateEndWith() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(null, OffsetDateTime.now())));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));
  }

  @Test
  public void resultFilterByFixedEvaluationDateRange() throws SQLException {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // this one is from before the filter StartDate
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    OffsetDateTime evaluationTimeOfFirstRun = OffsetDateTime.now().minusSeconds(2L);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(decisionDefinitionDto.getId(), evaluationTimeOfFirstRun);
    OffsetDateTime evaluationTimeAfterFirstRun = evaluationTimeOfFirstRun.plusSeconds(1L);

    decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(
      evaluationTimeAfterFirstRun,
      OffsetDateTime.now()
    )));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(5));
  }

  @Test
  public void resultFilterByRollingEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, "days")));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
  }

  @Test
  public void resultFilterByRollingEvaluationDateOutOfRange() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, "days")));

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    RawDataDecisionReportResultDto result = evaluateReportWithNewAuthToken(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(0L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));
  }

  private RawDataDecisionReportResultDto evaluateReportWithNewAuthToken(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponseWithNewToken(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataDecisionReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponseWithNewToken(DecisionReportDataDto reportData) {
    String header = "Bearer " + embeddedOptimizeRule.getNewAuthenticationToken();
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withGivenAuthHeader(header)
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
