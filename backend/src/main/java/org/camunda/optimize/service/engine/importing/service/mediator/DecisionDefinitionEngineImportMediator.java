package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.DecisionDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.DecisionDefinitionImportService;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionEngineImportMediator extends BackoffImportMediator<DecisionDefinitionImportIndexHandler> {

  @Autowired
  private DecisionDefinitionWriter processDefinitionWriter;

  private DecisionDefinitionFetcher engineEntityFetcher;
  private DecisionDefinitionImportService definitionImportService;

  @Autowired
  public DecisionDefinitionEngineImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getDecisionDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(DecisionDefinitionFetcher.class, engineContext);
    definitionImportService = new DecisionDefinitionImportService(
      processDefinitionWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    AllEntitiesBasedImportPage page = importIndexHandler.getNextPage();
    List<DecisionDefinitionEngineDto> entities = engineEntityFetcher.fetchProcessDefinitions(page);
    List<DecisionDefinitionEngineDto> newEntities = importIndexHandler.filterNewDefinitions(entities);
    if (!newEntities.isEmpty()) {
      importIndexHandler.addImportedDefinitions(newEntities);
      definitionImportService.executeImport(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
