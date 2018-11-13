package org.camunda.optimize.service.engine.importing.service.mediator;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.FinishedProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceEngineImportMediator
  extends BackoffImportMediator<FinishedProcessInstanceImportIndexHandler> {

  private FinishedProcessInstanceFetcher engineEntityFetcher;
  private FinishedProcessInstanceImportService finishedProcessInstanceImportService;
  @Autowired
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  public FinishedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getFinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(FinishedProcessInstanceFetcher.class, engineContext);
    finishedProcessInstanceImportService = new FinishedProcessInstanceImportService(
      finishedProcessInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    final List<HistoricProcessInstanceDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchHistoricFinishedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricProcessInstanceDto> nextPageEntities = engineEntityFetcher
      .fetchHistoricFinishedProcessInstances(page);

    if (!nextPageEntities.isEmpty()) {
      OffsetDateTime timestamp = nextPageEntities.get(nextPageEntities.size() - 1).getEndTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || !nextPageEntities.isEmpty()) {
      final List<HistoricProcessInstanceDto> allEntities = ListUtils.union(entitiesOfLastTimestamp, nextPageEntities);
      finishedProcessInstanceImportService.executeImport(allEntities);
    }

    return nextPageEntities.size() >= configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

}
