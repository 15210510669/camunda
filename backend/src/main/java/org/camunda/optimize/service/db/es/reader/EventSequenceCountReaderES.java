/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.db.reader.EventSequenceCountReader;
import org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.db.es.CompositeAggregationScroller;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.aggregations.metrics.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.COUNT;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.EVENT_NAME;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.GROUP;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.SOURCE;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.SOURCE_EVENT;
import static org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.TARGET_EVENT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;

@AllArgsConstructor
@Slf4j
public class EventSequenceCountReaderES implements EventSequenceCountReader {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public List<EventSequenceCountDto> getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
    final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents) {
    log.debug("Fetching event sequences for incoming and outgoing events");

    if (incomingEvents.isEmpty() && outgoingEvents.isEmpty()) {
      return Collections.emptyList();
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(buildSequencedEventsQuery(incomingEvents, outgoingEvents))
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve event sequence counts!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event sequence counts!", e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventSequenceCountDto.class, objectMapper);
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForAllExternalEventsUsingSearchTerm(final String searchTerm) {
    log.debug("Fetching all external event counts with searchTerm {}", searchTerm);
    return getEventCountsForSearchTerm(Collections.emptyList(), searchTerm);
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForExternalGroupsUsingSearchTerm(final List<String> groups,
                                                                                    final String searchTerm) {
    log.debug("Fetching external event counts with searchTerm {} for groups: {}", searchTerm, groups);
    return getEventCountsForSearchTerm(groups, searchTerm);
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForCamundaSources(final List<CamundaEventSourceEntryDto> camundaSources) {
    log.debug("Fetching camunda event counts for event sources: {}", camundaSources);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(matchAllQuery());
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final String[] indicesToSearch = camundaSources.stream()
      .map(source -> EventSequenceCountIndex.constructIndexName(source.getConfiguration().getProcessDefinitionKey()))
      .toList().toArray(new String[camundaSources.size()]);
    final SearchRequest searchRequest = new SearchRequest(indicesToSearch)
      .source(searchSourceBuilder);
    List<EventCountResponseDto> eventCountDtos = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION)
      .setCompositeBucketConsumer(bucket -> eventCountDtos.add(extractEventCounts(bucket)))
      .consumeAllPages();
    return eventCountDtos;
  }

  @Override
  public Set<String> getIndexSuffixesForCurrentSequenceCountIndices() {
    final GetAliasesResponse aliases;
    try {
      aliases = esClient.getAlias(new GetAliasesRequest(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*"));
    } catch (IOException e) {
      final String errorMessage = "Could not retrieve the index keys for sequence count indices!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName -> fullAliasName.substring(
        fullAliasName.lastIndexOf(EVENT_SEQUENCE_COUNT_INDEX_PREFIX) + EVENT_SEQUENCE_COUNT_INDEX_PREFIX.length()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public List<EventSequenceCountDto> getEventSequencesContainingBothEventTypes(final EventTypeDto firstEventTypeDto,
                                                                               final EventTypeDto secondEventTypeDto) {
    log.debug(
      "Fetching event sequences containing both event types: [{}] and [{}]",
      firstEventTypeDto,
      secondEventTypeDto
    );

    final BoolQueryBuilder query = boolQuery()
      .should(
        boolQuery()
          .must(buildEventTypeBoolQueryForProperty(firstEventTypeDto, SOURCE_EVENT))
          .must(buildEventTypeBoolQueryForProperty(secondEventTypeDto, TARGET_EVENT)))
      .should(
        boolQuery()
          .must(buildEventTypeBoolQueryForProperty(firstEventTypeDto, TARGET_EVENT))
          .must(buildEventTypeBoolQueryForProperty(secondEventTypeDto, SOURCE_EVENT)));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String errorMessage = "Was not able to retrieve event sequence counts for given event types!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventSequenceCountDto.class, objectMapper);
  }

  @Override
  public List<EventSequenceCountDto> getAllSequenceCounts() {
    log.debug("Fetching all event sequences for index key: {}", indexKey);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()));

    SearchResponse scrollResponse;
    try {
      scrollResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Was not able to retrieve event sequence counts for index key %s!",
        indexKey
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResponse,
      EventSequenceCountDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()
    );
  }

  private List<EventCountResponseDto> getEventCountsForSearchTerm(final List<String> groups, final String searchTerm) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    final BoolQueryBuilder query = buildCountRequestQuery(searchTerm);
    if (!CollectionUtils.isEmpty(groups)) {
      addGroupFilteringForQuery(groups, query);
    }
    searchSourceBuilder.query(query);
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder);
    List<EventCountResponseDto> eventCountDtos = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION)
      .setCompositeBucketConsumer(bucket -> eventCountDtos.add(extractEventCounts(bucket)))
      .consumeAllPages();
    return eventCountDtos;
  }

  private QueryBuilder buildSequencedEventsQuery(final List<EventTypeDto> incomingEvents,
                                                 final List<EventTypeDto> outgoingEvents) {
    final BoolQueryBuilder query = boolQuery();
    incomingEvents.forEach(eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, SOURCE_EVENT)));
    outgoingEvents.forEach(eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, TARGET_EVENT)));
    return query;
  }

  private BoolQueryBuilder buildEventTypeBoolQueryForProperty(EventTypeDto eventTypeDto, String propertyName) {
    BoolQueryBuilder boolQuery = boolQuery();
    getNullableFieldQuery(boolQuery, getNestedField(propertyName, GROUP), eventTypeDto.getGroup());
    getNullableFieldQuery(boolQuery, getNestedField(propertyName, SOURCE), eventTypeDto.getSource());
    boolQuery.must(termQuery(getNestedField(propertyName, EVENT_NAME), eventTypeDto.getEventName()));
    return boolQuery;
  }

  private void getNullableFieldQuery(BoolQueryBuilder builder, final String field, final String value) {
    if (value != null) {
      builder.must(termQuery(field, value));
      return;
    }
    builder.mustNot(existsQuery(field));
  }

  private BoolQueryBuilder buildCountRequestQuery(final String searchTerm) {
    if (searchTerm == null) {
      return boolQuery();
    }

    final String lowerCaseSearchTerm = searchTerm.toLowerCase();
    if (searchTerm.length() > MAX_GRAM) {
      return boolQuery()
        .should(prefixQuery(getNestedField(SOURCE_EVENT, GROUP), lowerCaseSearchTerm))
        .should(prefixQuery(getNestedField(SOURCE_EVENT, SOURCE), lowerCaseSearchTerm))
        .should(prefixQuery(getNestedField(SOURCE_EVENT, EVENT_NAME), lowerCaseSearchTerm));
    }

    return boolQuery().should(multiMatchQuery(
      lowerCaseSearchTerm,
      getNgramSearchField(GROUP),
      getNgramSearchField(SOURCE),
      getNgramSearchField(EVENT_NAME)
    ).analyzer(KEYWORD_ANALYZER));
  }

  private CompositeAggregationBuilder createAggregationBuilder() {
    final SumAggregationBuilder eventCountAggregation = sum(COUNT_AGG)
      .field(COUNT);

    List<CompositeValuesSourceBuilder<?>> eventAndSourceAndGroupTerms = new ArrayList<>();
    eventAndSourceAndGroupTerms.add(new TermsValuesSourceBuilder(EVENT_NAME_AGG)
                                      .field(SOURCE_EVENT + "." + EVENT_NAME));
    eventAndSourceAndGroupTerms.add(new TermsValuesSourceBuilder(SOURCE_AGG)
                                      .field(SOURCE_EVENT + "." + SOURCE)
                                      .missingBucket(true));
    eventAndSourceAndGroupTerms.add(new TermsValuesSourceBuilder(GROUP_AGG)
                                      .field(SOURCE_EVENT + "." + GROUP)
                                      .missingBucket(true));

    return new CompositeAggregationBuilder(
      COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION,
      eventAndSourceAndGroupTerms
    )
      .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
      .subAggregation(eventCountAggregation);
  }

  private void addGroupFilteringForQuery(final List<String> groups, final BoolQueryBuilder query) {
    final List<String> nonNullGroups = groups.stream()
      .filter(Objects::nonNull)
      .toList();
    final boolean includeNull = groups.size() > nonNullGroups.size();

    final BoolQueryBuilder groupFilterQuery = boolQuery().minimumShouldMatch(1);
    if (!nonNullGroups.isEmpty()) {
      groupFilterQuery.should(boolQuery().should(termsQuery(getNestedField(SOURCE_EVENT, GROUP), nonNullGroups)));
    }
    if (includeNull) {
      groupFilterQuery.should(boolQuery().mustNot(existsQuery(getNestedField(SOURCE_EVENT, GROUP))));
    }
    if (!groupFilterQuery.should().isEmpty()) {
      query.filter().add(groupFilterQuery);
    }
  }

  private EventCountResponseDto extractEventCounts(final ParsedComposite.ParsedBucket bucket) {
    final String eventName = (String) (bucket.getKey()).get(EVENT_NAME_AGG);
    final String source = (String) (bucket.getKey().get(SOURCE_AGG));
    final String group = (String) (bucket.getKey().get(GROUP_AGG));

    final long count = (long) ((Sum) bucket.getAggregations().get(COUNT_AGG)).getValue();

    return EventCountResponseDto.builder()
      .group(group)
      .source(source)
      .eventName(eventName)
      .count(count)
      .build();
  }

  private String getIndexName() {
    return EVENT_SEQUENCE_COUNT_INDEX_PREFIX + indexKey;
  }

  private String getNgramSearchField(final String searchFieldName) {
    return getNestedField(SOURCE_EVENT, searchFieldName) + "." + N_GRAM_FIELD;
  }

  private String getNestedField(final String property, final String searchFieldName) {
    return property + "." + searchFieldName;
  }

}
