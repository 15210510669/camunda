package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.Test;

import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class DecisionDefinitionRetrievalIT extends AbstractDecisionDefinitionIT {

  private final static String DECISION_DEFINITION_KEY = "aDecision";

  @Test
  public void getDecisionDefinitionsWithMoreThenTen() {
    for (int i = 0; i < 11; i++) {
      // given
      deployAndStartSimpleDecisionDefinition(DECISION_DEFINITION_KEY + i);
    }
    embeddedOptimizeRule.getConfigurationService().setEngineImportDecisionDefinitionXmlMaxPageSize(11);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    assertThat(definitions.size(), is(11));
  }

  @Test
  public void getDecisionDefinitionsWithoutXml() {
    // given
    String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
    assertThat(definitions.get(0).getKey(), is(decisionDefinitionKey));
    assertThat(definitions.get(0).getDmn10Xml(), nullValue());
  }

  @Test
  public void getDecisionDefinitionsWithXml() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployDecisionDefinition(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
    assertThat(definitions.get(0).getKey(), is(decisionDefinitionKey));
    assertThat(definitions.get(0).getDmn10Xml(), is(Dmn.convertToString(modelInstance)));
  }

  @Test
  public void getDecisionDefinitionsOnlyIncludeTheOnesWhereTheXmlIsImported() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    addDecisionDefinitionWithoutXmlToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
  }

  @Test
  public void getDecisionDefinitionXmlByKeyAndVersion() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployDecisionDefinition(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String actualXml = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionXmlRequest(
        decisionDefinitionEngineDto.getKey(),
        decisionDefinitionEngineDto.getVersion()
      )
      .execute(String.class, 200);

    // then
    assertThat(actualXml, is(Dmn.convertToString(modelInstance)));
  }

  @Test
  public void getDecisionDefinitionXmlByKeyAndAllVersionReturnsLatest() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    // first version
    final DmnModelInstance modelInstance1 = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto1 =
      engineRule.deployDecisionDefinition(modelInstance1);
    // second version
    final DmnModelInstance modelInstance2 = createSimpleDmnModel(decisionDefinitionKey);
    modelInstance2.getDefinitions().getDrgElements().stream().findFirst()
      .ifPresent(drgElement -> drgElement.setName("Add name to ensure that this is the latest version!"));
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto2 =
      engineRule.deployDecisionDefinition(modelInstance2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    final String actualXml = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionXmlRequest(decisionDefinitionEngineDto1.getKey(), ALL_VERSIONS)
      .execute(String.class, 200);

    // then
    assertThat(actualXml, is(Dmn.convertToString(modelInstance2)));
  }

  private void addDecisionDefinitionWithoutXmlToElasticsearch() {
    DecisionDefinitionOptimizeDto decisionDefinitionWithoutXml = new DecisionDefinitionOptimizeDto();
    decisionDefinitionWithoutXml.setDmn10Xml(null);
    decisionDefinitionWithoutXml.setKey("aDecDefKey");
    decisionDefinitionWithoutXml.setVersion("aDevDefVersion");
    decisionDefinitionWithoutXml.setId("aDecDefId");
    elasticSearchRule.addEntryToElasticsearch(
      ElasticsearchConstants.DECISION_DEFINITION_TYPE, "fooId", decisionDefinitionWithoutXml
    );
  }

}
