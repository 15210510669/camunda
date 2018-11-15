package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class ImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConfigurationService configurationService;

  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String id) {
    logger.debug("Fetching import index of type [{}]", id);
    GetResponse getResponse = null;
    try {
      getResponse = esclient
        .prepareGet(
          getOptimizeIndexAliasForType(configurationService.getImportIndexType()),
          configurationService.getImportIndexType(),
          id)
        .setRealtime(false)
        .get();
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      try {
        AllEntitiesBasedImportIndexDto storedIndex =
          objectMapper.readValue(getResponse.getSourceAsString(), AllEntitiesBasedImportIndexDto.class);
        return Optional.of(storedIndex);
      } catch (IOException e) {
        logger.error("Was not able to retrieve import index of [{}]. Reason: {}", id, e);
        return Optional.empty();
      }
    } else {
      logger.debug("Was not able to retrieve import index for type '{}' from Elasticsearch. " +
        "Desired index does not exist.", id);
      return Optional.empty();
    }
  }

}
