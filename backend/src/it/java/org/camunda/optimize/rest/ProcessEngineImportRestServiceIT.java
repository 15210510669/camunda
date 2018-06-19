package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;



public class ProcessEngineImportRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private static final String PROCESS_ID = "aProcessId";

  @Test
  public void importDataFromEngine() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    Response response = embeddedOptimizeRule.target("process-definition")
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    //then
    assertThat(response.getStatus(),is(200));
    List<ProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ProcessDefinitionOptimizeDto>>(){});

    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(),is(notNullValue()));
    assertThat(definitions.get(0).getKey(),is(PROCESS_ID));
    assertThat(definitions.get(0).getVersion(),is(not(0L)));
  }

}