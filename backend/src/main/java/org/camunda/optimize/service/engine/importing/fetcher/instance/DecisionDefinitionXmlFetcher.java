package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlFetcher extends RetryBackoffEngineEntityFetcher<DecisionDefinitionXmlEngineDto> {


  public DecisionDefinitionXmlFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<DecisionDefinitionXmlEngineDto> fetchXmlsForDefinitions(IdSetBasedImportPage page) {
    final Set<String> ids = page.getIds();
    return fetchXmlsForDefinitions(new ArrayList<>(ids));
  }

  private List<DecisionDefinitionXmlEngineDto> fetchXmlsForDefinitions(final List<String> decisionDefinitionIds) {
    logger.debug("Fetching decision definition xml ...");
    final List<DecisionDefinitionXmlEngineDto> xmls = new ArrayList<>(decisionDefinitionIds.size());
    final long requestStart = System.currentTimeMillis();
    for (String processDefinitionId : decisionDefinitionIds) {
      final List<DecisionDefinitionXmlEngineDto> singleXml = fetchWithRetry(
        () -> performGetDecisionDefinitionXmlRequest(processDefinitionId)
      );
      xmls.addAll(singleXml);
    }
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] decision definition xmls within [{}] ms", decisionDefinitionIds.size(), requestEnd - requestStart
    );
    return xmls;
  }

  private List<DecisionDefinitionXmlEngineDto> performGetDecisionDefinitionXmlRequest(final String decisionDefinitionId) {
    final DecisionDefinitionXmlEngineDto decisionDefinitionXmlEngineDto = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getDecisionDefinitionXmlEndpoint(decisionDefinitionId))
      .request(MediaType.APPLICATION_JSON)
      .get(DecisionDefinitionXmlEngineDto.class);
    return Collections.singletonList(decisionDefinitionXmlEngineDto);
  }
}
