package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ImportService;

import java.util.List;

public abstract class ScrollBasedImportMediator<T extends ScrollBasedImportIndexHandler, DTO>
  extends BackoffImportMediator<T> {

  protected ImportService<DTO> importService;

  public ScrollBasedImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  protected abstract List<DTO> getEntities(IdSetBasedImportPage page);


  @Override
  protected boolean importNextEnginePage() {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<DTO> entities = getEntities(page);
      if (!entities.isEmpty()) {
        importIndexHandler.updateIndex(page.getIds().size());
        importService.executeImport(entities);
      }
      return true;
    }
    return false;
  }

}
