/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_DEFINITION_ID_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_DEFINITION_NAME_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_2_1;
import static io.camunda.operate.data.util.DecisionDataUtil.PROCESS_INSTANCE_ID;
import static io.camunda.operate.webapp.rest.DecisionInstanceRestService.DECISION_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_DECISION_NAME;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_DECISION_VERSION;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_EVALUATION_DATE;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_ID;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_PROCESS_INSTANCE_ID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.SortingDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceForListDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests Elasticsearch queries for decision instances.
 */
public class DecisionListQueryIT extends OperateIntegrationTest {

  private static final String QUERY_INSTANCES_URL = DECISION_INSTANCE_URL;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private DecisionDataUtil testDataUtil;

  @Test
  public void testVariousQueries() throws Exception {
    createData();

    testQueryAll();
    testQueryEvaluated();
    testQueryFailed();

    testQueryByDecisionDefinitionId();
    testQueryByIds();
    testQueryByProcessInstanceId();
    testQueryByNonExistingDecisionDefinitionId();

    testPagination();

    testVariousSorting();

  }

  public void testQueryAll() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createGetAllDecisionInstancesRequest();

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(5);
    assertThat(response.getTotalCount()).isEqualTo(5);
  }

  public void testQueryEvaluated() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createDecisionInstanceRequest(
        q -> q.setCompleted(true)
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(3);
    assertThat(response.getDecisionInstances()).extracting(dec -> dec.getState()).containsOnly(
        DecisionInstanceStateDto.COMPLETED);
    assertThat(response.getTotalCount()).isEqualTo(3);
  }

  public void testQueryFailed() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createDecisionInstanceRequest(
        q -> q.setFailed(true)
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(2);
    assertThat(response.getDecisionInstances()).extracting(dec -> dec.getState()).containsOnly(
        DecisionInstanceStateDto.FAILED);
    assertThat(response.getTotalCount()).isEqualTo(2);
  }

  public void testQueryByDecisionDefinitionId() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createGetAllDecisionInstancesRequest(
        q -> q.setDecisionDefinitionIds(asList(DECISION_DEFINITION_ID_1))
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(2);
    assertThat(response.getDecisionInstances()).extracting(dec -> dec.getDecisionName()).containsOnly(
        DECISION_DEFINITION_NAME_1);
    assertThat(response.getTotalCount()).isEqualTo(2);
  }

  public void testQueryByNonExistingDecisionDefinitionId() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createGetAllDecisionInstancesRequest(
        q -> q.setDecisionDefinitionIds(asList("wrongId"))
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(0);
    assertThat(response.getTotalCount()).isEqualTo(0);
  }

  public void testQueryByIds() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createGetAllDecisionInstancesRequest(
        q -> q.setIds(asList(DECISION_INSTANCE_ID_1_1, DECISION_INSTANCE_ID_2_1))
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(2);
    assertThat(response.getDecisionInstances()).extracting(dec -> dec.getId()).containsOnly(
        DECISION_INSTANCE_ID_1_1, DECISION_INSTANCE_ID_2_1);
    assertThat(response.getTotalCount()).isEqualTo(2);
  }

  public void testQueryByProcessInstanceId() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceQueryDto = TestUtil.createGetAllDecisionInstancesRequest(
        q -> q.setProcessInstanceId(String.valueOf(PROCESS_INSTANCE_ID))
    );

    MvcResult mvcResult = postRequest(query(), decisionInstanceQueryDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionInstances().size()).isEqualTo(2);
    assertThat(response.getDecisionInstances()).extracting(dec -> dec.getProcessInstanceId()).containsOnly(
        String.valueOf(PROCESS_INSTANCE_ID));
    assertThat(response.getTotalCount()).isEqualTo(2);
  }

  @Test
  public void testQueryByEvaluationDate() throws Exception {
    //given
    final OffsetDateTime date1 = OffsetDateTime.of(2018, 1, 1, 15, 30, 30, 156, OffsetDateTime.now().getOffset());      //January 1, 2018
    final OffsetDateTime date2 = OffsetDateTime.of(2018, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset());      //February 1, 2018
    final OffsetDateTime date3 = OffsetDateTime.of(2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset());      //March 1, 2018
//    final OffsetDateTime date4 = OffsetDateTime.of(2018, 4, 1, 2, 12, 0, 0, OffsetDateTime.now().getOffset());          //April 1, 2018
    final DecisionInstanceEntity decisionInstance1 = testDataUtil.createDecisionInstance(date1);
    final DecisionInstanceEntity decisionInstance2 = testDataUtil.createDecisionInstance(date2);
    final DecisionInstanceEntity decisionInstance3 = testDataUtil.createDecisionInstance(date3);
    elasticsearchTestRule.persistNew(decisionInstance1, decisionInstance2, decisionInstance3);

    //when
    DecisionInstanceListRequestDto query = TestUtil.createGetAllDecisionInstancesRequest(q -> {
      q.setEvaluationDateAfter(date1.minus(1, ChronoUnit.DAYS));
      q.setEvaluationDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #1", decisionInstance1.getId(), decisionInstance2.getId());

    //test inclusion for startDateAfter and exclusion for startDateBefore
    //when
    query = TestUtil.createGetAllDecisionInstancesRequest(q -> {
      q.setEvaluationDateAfter(date1);
      q.setEvaluationDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #2", decisionInstance1.getId(), decisionInstance2.getId());

    //when
    query = TestUtil.createGetAllDecisionInstancesRequest(q -> {
      q.setEvaluationDateAfter(date1.plus(1, ChronoUnit.MILLIS));
      q.setEvaluationDateBefore(date3.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #3", decisionInstance2.getId(), decisionInstance3.getId());

  }

  private void requestAndAssertIds(DecisionInstanceListRequestDto request, String testCaseName, String... ids) throws Exception {
    //then
    MvcResult mvcResult = postRequest(query(), request);
    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });

    assertThat(response.getDecisionInstances()).as(testCaseName).extracting(
        DecisionInstanceTemplate.ID).containsExactlyInAnyOrder(ids);
  }

  private void testPagination() throws Exception {
    //query running instances
    DecisionInstanceListRequestDto decisionInstanceRequest = TestUtil.createGetAllDecisionInstancesRequest();
    decisionInstanceRequest.setPageSize(2);

    //page 1
    MvcResult mvcResult = postRequest(query(), decisionInstanceRequest);
    DecisionInstanceListResponseDto page1Response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
    assertThat(page1Response.getDecisionInstances().size()).isEqualTo(2);
    assertThat(page1Response.getTotalCount()).isEqualTo(5);

    //page 2
    decisionInstanceRequest.setSearchAfter(
        page1Response.getDecisionInstances().get(page1Response.getDecisionInstances().size() - 1)
            .getSortValues());
    decisionInstanceRequest.setPageSize(3);
    mvcResult = postRequest(query(), decisionInstanceRequest);
    DecisionInstanceListResponseDto page2Response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
    assertThat(page2Response.getDecisionInstances().size()).isEqualTo(3);
    assertThat(page2Response.getTotalCount()).isEqualTo(5);
    assertThat(page2Response.getDecisionInstances()).doesNotContainAnyElementsOf(page1Response.getDecisionInstances());

    //page 1 via searchBefore
    decisionInstanceRequest.setSearchAfter(null);
    decisionInstanceRequest.setSearchBefore(
        page2Response.getDecisionInstances().get(0).getSortValues());
    decisionInstanceRequest.setPageSize(5);
    mvcResult = postRequest(query(), decisionInstanceRequest);
    DecisionInstanceListResponseDto page1Response2 = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
    assertThat(page1Response2.getDecisionInstances().size()).isEqualTo(2);
    assertThat(page1Response2.getTotalCount()).isEqualTo(5);
    assertThat(page1Response.getDecisionInstances()).containsExactlyInAnyOrderElementsOf(page1Response2.getDecisionInstances());
  }

  private void testSorting(SortingDto sorting, Comparator<DecisionInstanceForListDto> comparator,
      String sortingDescription) throws Exception {

    //query running instances
    DecisionInstanceListRequestDto decisionInstanceRequestDto = TestUtil.createGetAllDecisionInstancesRequest();
    if(sorting!=null) {
      decisionInstanceRequestDto.setSorting(sorting);
    }

    MvcResult mvcResult = postRequest(query(), decisionInstanceRequestDto);

    DecisionInstanceListResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });

    assertThat(response.getDecisionInstances().size()).as("List size sorted by %s.", sortingDescription).isEqualTo(5);

    assertThat(response.getDecisionInstances()).as("Sorted by %s.", sortingDescription).isSortedAccordingTo(comparator);
  }

  public void testVariousSorting() throws Exception {
    testSortingByDecisionNameAsc();
    testSortingByDecisionNameDesc();
    testSortingByDecisionVersionAsc();
    testSortingByDecisionVersionDesc();
    testSortingByEvaluationDateAsc();
    testSortingByEvaluationDateDesc();
    testDefaultSorting();
    testSortingByDecisionInstancIdAsc();
    testSortingByDecisionInstancIdDesc();
    testSortingByProcessInstancIdAsc();
    testSortingByProcessInstancIdDesc();
  }

  private void testSortingByEvaluationDateAsc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = Comparator.comparing(DecisionInstanceForListDto::getEvaluationDate);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_EVALUATION_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "evaluationTime asc");
  }

  private void testSortingByEvaluationDateDesc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> o2.getEvaluationDate().compareTo(o1.getEvaluationDate());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_EVALUATION_DATE);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "evaluationTime desc");
  }

  private void testDefaultSorting() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = Comparator.comparing(o -> getId(o));
    testSorting(null, comparator, "default");
  }

  private void testSortingByDecisionInstancIdAsc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = Comparator.comparing(o -> getId(o));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "id asc");
  }

  private void testSortingByDecisionInstancIdDesc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> getId(o2).compareTo(getId(o1));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "id desc");
  }

  private void testSortingByDecisionNameAsc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator =
        Comparator.comparing((DecisionInstanceForListDto o) -> o.getDecisionName().toLowerCase())
          .thenComparingLong(o -> getId(o));
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_DECISION_NAME);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "decisionName acs");
  }

  @NotNull
  private Long getId(final DecisionInstanceForListDto o) {
    return Long.valueOf(o.getId().split("-")[0]);
  }

  private void testSortingByDecisionNameDesc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> {
      int x = o2.getDecisionName().toLowerCase().compareTo(o1.getDecisionName().toLowerCase());
      if (x == 0) {
        x = getId(o1).compareTo(getId(o2));
      }
      return x;
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_DECISION_NAME);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "decisionName desc");
  }

  private void testSortingByDecisionVersionAsc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = Comparator
        .comparing(DecisionInstanceForListDto::getDecisionVersion);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_DECISION_VERSION);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "decisionVersion asc");
  }

  private void testSortingByDecisionVersionDesc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> o2.getDecisionVersion()
        .compareTo(o1.getDecisionVersion());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_DECISION_VERSION);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "decisionVersion desc");
  }

  private void testSortingByProcessInstancIdDesc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> {
      int x;
      if (o1.getProcessInstanceId() == null && o2.getProcessInstanceId() == null) {
        x = 0;
      } else if (o1.getProcessInstanceId() == null) {
        x = 1;
      } else if (o2.getProcessInstanceId() == null) {
        x = -1;
      } else {
        x = Long.valueOf(o2.getProcessInstanceId()).compareTo(Long.valueOf(o1.getProcessInstanceId()));
      }
      if (x == 0) {
        x = getId(o1).compareTo(getId(o2));
      }
      return x;
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_PROCESS_INSTANCE_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE);

    testSorting(sorting, comparator, "processInstanceId desc");
  }

  private void testSortingByProcessInstancIdAsc() throws Exception {
    final Comparator<DecisionInstanceForListDto> comparator = (o1, o2) -> {
      int x;
      if (o1.getProcessInstanceId() == null && o2.getProcessInstanceId() == null) {
        x = 0;
      } else if (o1.getProcessInstanceId() == null) {
        x = 1;
      } else if (o2.getProcessInstanceId() == null) {
        x = -1;
      } else {
        x = Long.valueOf(o1.getProcessInstanceId()).compareTo(Long.valueOf(o2.getProcessInstanceId()));
      }
      if (x == 0) {
        x = getId(o1).compareTo(getId(o2));
      }
      return x;
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy(SORT_BY_PROCESS_INSTANCE_ID);
    sorting.setSortOrder(SortingDto.SORT_ORDER_ASC_VALUE);

    testSorting(sorting, comparator, "processInstanceId asc");
  }

  @Test
  public void testSortingByWrongValue() throws Exception {
    // when
    final String wrongSortParameter = "bpmnProcessId";
    final String query =
        "{\"query\": {\"completed\": true},"
            + "\"sorting\": { \"sortBy\": \""
            + wrongSortParameter
            + "\"}}}";
    MockHttpServletRequestBuilder request =
        post(query()).content(query.getBytes()).contentType(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();

    // then
    assertErrorMessageContains(
        mvcResult, "SortBy parameter has invalid value: " + wrongSortParameter);
  }

  protected void createData() throws PersistenceException {
    elasticsearchTestRule.persistOperateEntitiesNew(testDataUtil.createDecisionInstances());
  }

  private String query() {
    return QUERY_INSTANCES_URL;
  }

}


