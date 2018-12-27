package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class FlowNodeRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void mapFlowNodeWithoutAuthentication() {
    //given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey("aKey");
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void getFlowNodesWithNullNullParameter() {
    //given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(null);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(200));
  }

  private void createProcessDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    expected.setId(expectedProcessDefinitionId);
    expected.setKey(processDefinitionKey);
    expected.setVersion(processDefinitionVersion);
    expected.setEngine("testEngine");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
    createProcessDefinitionXml(processDefinitionKey, processDefinitionVersion);
  }

  private void createProcessDefinitionXml(String processDefinitionKey, String processDefinitionVersion) {
    ProcessDefinitionOptimizeDto expectedXml = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    expectedXml.setBpmn20Xml("XML123");
    expectedXml.setKey(processDefinitionKey);
    expectedXml.setVersion(processDefinitionVersion);
    expectedXml.setId(expectedProcessDefinitionId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expectedXml);
  }
}
