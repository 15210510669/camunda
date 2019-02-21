package org.camunda.optimize.service.es.report.decision;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractDecisionDefinitionIT {
  protected static final String OUTPUT_CLASSIFICATION_ID = "clause3";
  protected static final String OUTPUT_AUDIT_ID = "OutputClause_1ur6jbl";
  protected static final String INPUT_AMOUNT_ID = "clause1";
  protected static final String INPUT_CATEGORY_ID = "InputClause_15qmk0v";
  protected static final String INPUT_INVOICE_DATE_ID = "InputClause_0qixz9e";
  protected static final String INPUT_VARIABLE_INVOICE_CATEGORY = "invoiceCategory";
  protected static final String INPUT_VARIABLE_AMOUNT = "amount";
  protected static final String INPUT_VARIABLE_INVOICE_DATE = "invoiceDate";

  // dish variables
  protected static final String INPUT_SEASON_ID = "InputData_0rin549";
  protected static final String INPUT_NUMBER_OF_GUESTS_ID = "InputData_1axnom3";
  protected static final String INPUT_GUEST_WITH_CHILDREN_ID = "InputData_0pgvdj9";
  protected static final String INPUT_VARIABLE_SEASON = "season";
  protected static final String INPUT_VARIABLE_NUMBER_OF_GUESTS = "guestCount";
  protected static final String INPUT_VARIABLE_GUEST_WITH_CHILDREN = "guestsWithChildren";


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  private static String getInputVariableNameForId(String inputId) {
    switch (inputId) {
      case INPUT_AMOUNT_ID:
        return INPUT_VARIABLE_AMOUNT;
      case INPUT_CATEGORY_ID:
        return INPUT_VARIABLE_INVOICE_CATEGORY;
      case INPUT_INVOICE_DATE_ID:
        return INPUT_VARIABLE_INVOICE_DATE;
      case INPUT_SEASON_ID:
        return INPUT_VARIABLE_SEASON;
      case INPUT_NUMBER_OF_GUESTS_ID:
        return INPUT_VARIABLE_NUMBER_OF_GUESTS;
      case INPUT_GUEST_WITH_CHILDREN_ID:
        return INPUT_VARIABLE_GUEST_WITH_CHILDREN;
      default:
        throw new IllegalStateException("Unsupported inputVariableId: " + inputId);
    }
  }

  protected DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition(String decisionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineRule.deployAndStartDecisionDefinition(modelInstance);
  }

  protected DecisionDefinitionEngineDto deployDecisionDefinitionWithDifferentKey(final String key) {
    final DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(
      getClass().getClassLoader().getResourceAsStream(EngineIntegrationRule.DEFAULT_DMN_DEFINITION_PATH)
    );
    dmnModelInstance.getDefinitions().getDrgElements().stream()
      .findFirst()
      .ifPresent(drgElement -> drgElement.setId(key));
    return engineRule.deployDecisionDefinition(dmnModelInstance);
  }

  protected HashMap<String, InputVariableEntry> createInputs(final double amountValue,
                                                             final String category) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_AMOUNT_ID, new InputVariableEntry(INPUT_AMOUNT_ID, "Invoice Amount", VariableType.DOUBLE, amountValue));
      put(
        INPUT_CATEGORY_ID,
        new InputVariableEntry(INPUT_CATEGORY_ID, "Invoice Category", VariableType.STRING, category)
      );
    }};
  }

  protected HashMap<String, InputVariableEntry> createInputsWithDate(final double amountValue, final String invoiceDateTime) {
    final HashMap<String, InputVariableEntry> inputs = createInputs(amountValue, "Misc");
    inputs.put(
      INPUT_INVOICE_DATE_ID,
      new InputVariableEntry(INPUT_INVOICE_DATE_ID, "Invoice Date", VariableType.DATE, invoiceDateTime)
    );
    return inputs;
  }

  protected void startDecisionInstanceWithInputVars(final String id,
                                                    final HashMap<String, InputVariableEntry> inputVariables) {
    engineRule.startDecisionInstance(
      id,
      inputVariables.entrySet().stream().collect(toMap(
        entry -> getInputVariableNameForId(entry.getKey()),
        entry -> entry.getValue().getValue()
      ))
    );
  }

  protected DecisionReportMapResultDto evaluateMapReport(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(DecisionReportMapResultDto.class);
  }

  protected DecisionReportNumberResultDto evaluateNumberReport(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(DecisionReportNumberResultDto.class);
  }

  protected RawDataDecisionReportResultDto evaluateRawReport(DecisionReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataDecisionReportResultDto.class);
  }

  protected Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }
}
