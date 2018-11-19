package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlEngineImportMediator
  extends BackoffImportMediator<ProcessDefinitionXmlImportIndexHandler> {

  private ProcessDefinitionXmlFetcher engineEntityFetcher;
  private ProcessDefinitionXmlImportService definitionXmlImportService;
  @Autowired
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionXmlImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionXmlFetcher.class, engineContext);

    definitionXmlImportService = new ProcessDefinitionXmlImportService(
      processDefinitionXmlWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<ProcessDefinitionXmlEngineDto> entities = engineEntityFetcher.fetchXmlsForDefinitions(page);
      if (!entities.isEmpty()) {
        definitionXmlImportService.executeImport(entities);
      }
      return true;
    }
    return false;
  }

}
