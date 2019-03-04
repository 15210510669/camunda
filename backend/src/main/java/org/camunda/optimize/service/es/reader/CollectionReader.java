package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.CollectionType.DATA;
import static org.camunda.optimize.service.es.schema.type.CollectionType.NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

@Component
public class CollectionReader {
  private static final Logger logger = LoggerFactory.getLogger(CollectionReader.class);
  private final RestHighLevelClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Autowired
  public CollectionReader(RestHighLevelClient esClient,
                          final ConfigurationService configurationService,
                          final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public SimpleCollectionDefinitionDto getCollection(String collectionId) {
    logger.debug("Fetching collection with id [{}]", collectionId);
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(COLLECTION_TYPE),
      COLLECTION_TYPE,
      collectionId
    )
      .realtime(false);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch collection with id [%s]", collectionId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SimpleCollectionDefinitionDto.class);
      } catch (IOException e) {
        String reason = "Could not deserialize collection information for collection " + collectionId;
        logger.error("Was not able to retrieve collection with id [{}] from Elasticsearch. Reason: reason");
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      logger.error("Was not able to retrieve collection with id [{}] from Elasticsearch.", collectionId);
      throw new NotFoundException("Collection does not exist! Tried to retried collection with id " + collectionId);
    }
  }

  public List<ResolvedCollectionDefinitionDto> getAllResolvedCollections() {
    List<SimpleCollectionDefinitionDto> allCollections = getAllCollections();
    Set<String> entityIds = getAllEntityIdsFromCollections(allCollections);

    final Map<String, CollectionEntity> entityIdToEntityMap = getAllEntities()
      .stream()
      .collect(toMap(CollectionEntity::getId, r -> r));

    logger.debug("Mapping all available entity collections to resolved entity collections.");
    return allCollections.stream()
      .map(c -> mapToResolvedCollection(c, entityIdToEntityMap))
      .collect(Collectors.toList());
  }

  private List<CollectionEntity> getAllEntities() {
    logger.debug("Fetching all available entities for collections");
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(
        getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
        getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
        getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
        getOptimizeIndexAliasForType(DASHBOARD_TYPE)
      )
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  private Set<String> getAllEntityIdsFromCollections(List<SimpleCollectionDefinitionDto> allCollections) {
    return allCollections.stream()
      .map(SimpleCollectionDefinitionDto::getData)
      .filter(Objects::nonNull)
      .map(CollectionDataDto::getEntities)
      .flatMap(List::stream)
      .collect(Collectors.toSet());
  }

  private ResolvedCollectionDefinitionDto mapToResolvedCollection(SimpleCollectionDefinitionDto c,
                                                                  Map<String, CollectionEntity> entityIdToEntityMap) {
    ResolvedCollectionDefinitionDto resolvedCollection =
      new ResolvedCollectionDefinitionDto();
    resolvedCollection.setId(c.getId());
    resolvedCollection.setName(c.getName());
    resolvedCollection.setLastModifier(c.getLastModifier());
    resolvedCollection.setOwner(c.getOwner());
    resolvedCollection.setCreated(c.getCreated());
    resolvedCollection.setLastModified(c.getLastModified());

    if (c.getData() != null) {
      CollectionDataDto<String> collectionData = c.getData();
      CollectionDataDto<CollectionEntity> resolvedCollectionData = new CollectionDataDto<>();
      resolvedCollectionData.setConfiguration(c.getData().getConfiguration());
      resolvedCollectionData.setEntities(
        collectionData.getEntities()
          .stream()
          .map(entityIdToEntityMap::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList())
      );
      resolvedCollection.setData(resolvedCollectionData);
    }
    return resolvedCollection;
  }

  private List<SimpleCollectionDefinitionDto> getAllCollections() {
    logger.debug("Fetching all available collections");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .sort(NAME, SortOrder.ASC)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE))
        .types(COLLECTION_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Was not able to retrieve collections!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve collections!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      SimpleCollectionDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<SimpleCollectionDefinitionDto> findFirstCollectionsForEntity(String entityId) {
    logger.debug("Fetching collections using entity with id {}", entityId);

    final QueryBuilder getCollectionByEntityIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        DATA,
        QueryBuilders.termQuery("data.entities", entityId),
        ScoreMode.None
      ));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(getCollectionByEntityIdQuery)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE))
        .types(COLLECTION_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch collections for entity with id [%s]", entityId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), SimpleCollectionDefinitionDto.class, objectMapper);
  }
}
