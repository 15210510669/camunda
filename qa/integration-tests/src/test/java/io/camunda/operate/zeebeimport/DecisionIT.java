/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class DecisionIT extends OperateZeebeIntegrationTest {

  private static final String QUERY_DECISIONS_GROUPED_URL = "/api/decisions/grouped";
  private static final String QUERY_DECISION_XML_URL = "/api/decisions/%s/xml";

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private DecisionDataUtil testDataUtil;

  @Test
  public void testDecisionsGrouped() throws Exception {
    //given
    final String demoDecisionId1 = "invoiceClassification";
    final String decision1Name = "Invoice Classification";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision2Name = "Assign Approver Group";

    tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil()
        .decisionsAreDeployed(2);

    //when
    MockHttpServletRequestBuilder request = get(QUERY_DECISIONS_GROUPED_URL);
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    //then
    List<DecisionGroupDto> decisionGroupDtos = mockMvcTestRule.listFromResponse(mvcResult, DecisionGroupDto.class);
    assertThat(decisionGroupDtos).hasSize(2);
    assertThat(decisionGroupDtos).isSortedAccordingTo(new DecisionGroupDto.DecisionGroupComparator());

    assertThat(decisionGroupDtos).filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId1)).hasSize(1);
    final DecisionGroupDto demoDecisionGroup1 =
        decisionGroupDtos.stream().filter(wg -> wg.getDecisionId().equals(demoDecisionId1)).findFirst().get();
    assertThat(demoDecisionGroup1.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup1.getName()).isEqualTo(decision1Name);
    assertThat(demoDecisionGroup1.getDecisions()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup1.getDecisions().get(0).getId()).isNotEqualTo(demoDecisionGroup1.getDecisions().get(1).getId());

    assertThat(decisionGroupDtos).filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId2)).hasSize(1);
    final DecisionGroupDto demoDecisionGroup2 =
        decisionGroupDtos.stream().filter(wg -> wg.getDecisionId().equals(demoDecisionId2)).findFirst().get();
    assertThat(demoDecisionGroup2.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup2.getName()).isEqualTo(decision2Name);
    assertThat(demoDecisionGroup2.getDecisions()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup2.getDecisions().get(0).getId()).isNotEqualTo(demoDecisionGroup1.getDecisions().get(1).getId());

  }

  @Test
  public void testDecisionGetDiagramV1() throws Exception {
    //given
    final Long decision1V1Id = 1222L;
    final Long decision2V1Id = 1333L;

    createData();

    //when invoiceClassification version 1
    MockHttpServletRequestBuilder request = get(String.format(QUERY_DECISION_XML_URL, decision1V1Id));
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn();

    final String invoiceClassification_v_1 = mvcResult.getResponse().getContentAsString();

    //and invoiceClassification version 1
    request = get(String.format(QUERY_DECISION_XML_URL, decision2V1Id));
    mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn();

    final String invoiceAssignApprover_v_1 = mvcResult.getResponse().getContentAsString();

    //then one and the same DRD is returned
    assertThat(invoiceAssignApprover_v_1).isEqualTo(invoiceClassification_v_1);

    //it is of version 1
    assertThat(invoiceAssignApprover_v_1).isNotEmpty();
    assertThat(invoiceAssignApprover_v_1).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(invoiceAssignApprover_v_1).doesNotContain("exceptional");
  }

  @Test
  public void testDecisionGetDiagramV2() throws Exception {
    //given
    final Long decision1V2Id = 2222L;
    final Long decision2V2Id = 2333L;

    createData();

    //when invoiceClassification version 2
    MockHttpServletRequestBuilder request = get(String.format(QUERY_DECISION_XML_URL, decision1V2Id));
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn();

    final String invoiceClassification_v_2 = mvcResult.getResponse().getContentAsString();

    //and invoiceClassification version 2
    request = get(String.format(QUERY_DECISION_XML_URL, decision2V2Id));
    mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn();

    final String invoiceAssignApprover_v_2 = mvcResult.getResponse().getContentAsString();

    //then
    //one and the same DRD is returned
    assertThat(invoiceAssignApprover_v_2).isEqualTo(invoiceClassification_v_2);
    //it is of version 2
    assertThat(invoiceAssignApprover_v_2).isNotEmpty();
    assertThat(invoiceAssignApprover_v_2).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(invoiceAssignApprover_v_2).contains("exceptional");
  }

  @Test
  public void testNonExistingDecisionGetDiagram() throws Exception {
    //given
    final String decisionDefinitionId = "111";
    //no decisions deployed

    //when
    MockHttpServletRequestBuilder request = get(String.format(QUERY_DECISION_XML_URL, decisionDefinitionId));
    mockMvc.perform(request)
        .andExpect(status().isNotFound());
  }

  private void createData() throws PersistenceException {
    elasticsearchTestRule.persistOperateEntitiesNew(testDataUtil.createDecisionDefinitions());
  }

}
