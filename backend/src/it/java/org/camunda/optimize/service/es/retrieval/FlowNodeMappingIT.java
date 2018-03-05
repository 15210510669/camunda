package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class FlowNodeMappingIT {
  public static final String A_START = "aStart";
  public static final String A_TASK = "aTask";
  public static final String AN_END = "anEnd";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void mapFlowNodeIdsToNames() throws Exception {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    Response response =
        embeddedOptimizeRule.target("flow-node/flowNodeNames")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(Entity.json(flowNodeIdsToNamesRequestDto));

    // then
    FlowNodeNamesResponseDto result = response.readEntity(FlowNodeNamesResponseDto.class);
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(3));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
    assertThat(result.getFlowNodeNames().values().contains(A_TASK), is(true));
    assertThat(result.getFlowNodeNames().values().contains(AN_END), is(true));
  }

  private BpmnModelInstance getNamedBpmnModelInstance() {
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .name(A_START)
          .serviceTask()
          .name(A_TASK)
          .camundaExpression("${true}")
        .endEvent()
          .name(AN_END)
        .done();
  }

  @Test
  public void mapFilteredFlowNodeIdsToNames() throws Exception {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    StartEvent start = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();


    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    List<String> ids = new ArrayList<>();
    ids.add(start.getId());
    flowNodeIdsToNamesRequestDto.setNodeIds(ids);
    Response response =
        embeddedOptimizeRule.target("flow-node/flowNodeNames")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(Entity.json(flowNodeIdsToNamesRequestDto));

    // then
    FlowNodeNamesResponseDto result = response.readEntity(FlowNodeNamesResponseDto.class);
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(1));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
  }
}
