/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.util.CompositeAggregationScroller;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.AbstractQueryBuilder;
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
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.COUNT;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.EVENT_NAME;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.GROUP;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.SOURCE;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.SOURCE_EVENT;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.TARGET_EVENT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;

@AllArgsConstructor
@Slf4j
public class EventSequenceCountReader {

  private static final String GROUP_AGG = EventCountDto.Fields.group;
  private static final String SOURCE_AGG = EventCountDto.Fields.source;
  private static final String EVENT_NAME_AGG = EventCountDto.Fields.eventName;
  private static final String COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION =
    "compositeEventNameSourceAndGroupAggregation";
  private static final String COUNT_AGG = EventCountDto.Fields.count;
  private static final String KEYWORD_ANALYZER = "keyword";

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

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
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve event sequence counts!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event sequence counts!", e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventSequenceCountDto.class, objectMapper);
  }

  public List<EventCountDto> getEventCountsWithSearchTerm(final String searchTerm) {
    log.debug("Fetching event counts with searchTerm [{}}]", searchTerm);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(buildCountRequestQuery(searchTerm));
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder);
    List<EventCountDto> eventCountDtos = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION)
      .setCompositeBucketConsumer(bucket -> eventCountDtos.add(extractEventCounts(bucket)))
      .scroll();
    return eventCountDtos;
  }

  public List<EventCountDto> getEventCountsForCamundaSources(final List<EventSourceEntryDto> eventSourceEntryDtos) {
    log.debug("Fetching event counts for event sources: {}", eventSourceEntryDtos);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(matchAllQuery());
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final String[] indicesToSearch = eventSourceEntryDtos.stream()
      .map(source -> new EventSequenceCountIndex(source.getProcessDefinitionKey()).getIndexName())
      .collect(Collectors.toList()).toArray(new String[eventSourceEntryDtos.size()]);
    final SearchRequest searchRequest = new SearchRequest(indicesToSearch)
      .source(searchSourceBuilder);
    List<EventCountDto> eventCountDtos = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION)
      .setCompositeBucketConsumer(bucket -> eventCountDtos.add(extractEventCounts(bucket)))
      .scroll();
    return eventCountDtos;
  }

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
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = "Was not able to retrieve event sequence counts for given event types!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventSequenceCountDto.class, objectMapper);
  }

  public List<EventSequenceCountDto> getAllSequenceCounts() {
    log.debug("Fetching all event sequences for index key: {}", indexKey);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getIndexName())
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    SearchResponse scrollResponse;
    try {
      scrollResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
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
      configurationService.getEsScrollTimeoutInSeconds()
    );
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

  private AbstractQueryBuilder<?> buildCountRequestQuery(final String searchTerm) {
    if (searchTerm == null) {
      return matchAllQuery();
    }

    final String lowerCaseSearchTerm = searchTerm.toLowerCase();
    if (searchTerm.length() > IndexSettingsBuilder.MAX_GRAM) {
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
      .size(configurationService.getEsAggregationBucketLimit())
      .subAggregation(eventCountAggregation);
  }

  private EventCountDto extractEventCounts(final ParsedComposite.ParsedBucket bucket) {
    final String eventName = (String) (bucket.getKey()).get(EVENT_NAME_AGG);
    final String source = (String) (bucket.getKey().get(SOURCE_AGG));
    final String group = (String) (bucket.getKey().get(GROUP_AGG));

    final long count = (long) ((Sum) bucket.getAggregations().get(COUNT_AGG)).getValue();

    return EventCountDto.builder()
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
