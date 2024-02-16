/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.LongArrayList;

public class MetricsExporter implements Exporter {

  /**
   * Defines the time after instances or jobs are getting removed from the cache/map.
   *
   * <p>Using large TTL or increasing the current needs to be done with care. Right now we assume
   * that we can create at max ~150 PI per partition per second.
   *
   * <p>This means: 150 * TIME_TO_LIVE = max_cache_size max_cache_size * 4 (cache counts) * 8 (long)
   * * 8 (long) = estimated_max_used_spaced_per_partition (roughly)
   * estimated_max_used_spaced_per_partition * partitions = max_used_spaced_per_broker When using a
   * TTL of 60 seconds this means: max_cache_size = 9000 (entries)
   * estimated_max_used_spaced_per_partition = 2.304.000 (bytes) = 2.3 MB
   *
   * <p>The estimated_max_used_spaced_per_partition is slightly a little more, since the TreeMap
   * might consume more memory than the Long2LongHashMap.
   */
  public static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);

  private final ExecutionLatencyMetrics executionLatencyMetrics;
  private final Long2LongHashMap jobKeyToCreationTimeMap;
  private final Long2LongHashMap processInstanceKeyToCreationTimeMap;

  // only used to keep track of how long the entries are existing and to clean up the corresponding
  // maps
  private final NavigableMap<Long, LongArrayList> creationTimeToJobKeyNavigableMap;
  private final NavigableMap<Long, LongArrayList> creationTimeToProcessInstanceKeyNavigableMap;

  private Controller controller;

  public MetricsExporter() {
    this(new ExecutionLatencyMetrics());
  }

  public MetricsExporter(final ExecutionLatencyMetrics executionLatencyMetrics) {
    this.executionLatencyMetrics = executionLatencyMetrics;
    jobKeyToCreationTimeMap = new Long2LongHashMap(-1);
    processInstanceKeyToCreationTimeMap = new Long2LongHashMap(-1);
    creationTimeToJobKeyNavigableMap = new TreeMap<>();
    creationTimeToProcessInstanceKeyNavigableMap = new TreeMap<>();
  }

  @Override
  public void configure(final Context context) throws Exception {
    context.setFilter(
        new RecordFilter() {
          private static final Set<ValueType> ACCEPTED_VALUE_TYPES =
              Set.of(ValueType.JOB, ValueType.JOB_BATCH, ValueType.PROCESS_INSTANCE);

          @Override
          public boolean acceptType(final RecordType recordType) {
            return recordType == RecordType.EVENT;
          }

          @Override
          public boolean acceptValue(final ValueType valueType) {
            return ACCEPTED_VALUE_TYPES.contains(valueType);
          }
        });
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    controller.scheduleCancellableTask(TIME_TO_LIVE, this::cleanUp);
  }

  @Override
  public void close() {
    jobKeyToCreationTimeMap.clear();
    processInstanceKeyToCreationTimeMap.clear();
    creationTimeToJobKeyNavigableMap.clear();
    creationTimeToProcessInstanceKeyNavigableMap.clear();
  }

  @Override
  public void export(final Record<?> record) {
    if (record.getRecordType() != RecordType.EVENT) {
      controller.updateLastExportedRecordPosition(record.getPosition());
      return;
    }

    final var partitionId = record.getPartitionId();
    final var recordKey = record.getKey();

    final var currentValueType = record.getValueType();
    if (currentValueType == ValueType.JOB) {
      handleJobRecord(record, partitionId, recordKey);
    } else if (currentValueType == ValueType.JOB_BATCH) {
      handleJobBatchRecord(record, partitionId);
    } else if (currentValueType == ValueType.PROCESS_INSTANCE) {
      handleProcessInstanceRecord(record, partitionId, recordKey);
    }

    controller.updateLastExportedRecordPosition(record.getPosition());
  }

  private void handleProcessInstanceRecord(
      final Record<?> record, final int partitionId, final long recordKey) {
    final var currentIntent = record.getIntent();

    if (currentIntent == ProcessInstanceIntent.ELEMENT_ACTIVATING
        && isProcessInstanceRecord(record)) {
      storeProcessInstanceCreation(record.getTimestamp(), recordKey);
    } else if (currentIntent == ProcessInstanceIntent.ELEMENT_COMPLETED
        && isProcessInstanceRecord(record)) {
      final var creationTime = processInstanceKeyToCreationTimeMap.remove(recordKey);
      executionLatencyMetrics.observeProcessInstanceExecutionTime(
          partitionId, creationTime, record.getTimestamp());
    }
    executionLatencyMetrics.setCurrentProcessInstanceCount(
        partitionId, processInstanceKeyToCreationTimeMap.size());
  }

  private void storeProcessInstanceCreation(final long creationTime, final long recordKey) {
    processInstanceKeyToCreationTimeMap.put(recordKey, creationTime);
    creationTimeToProcessInstanceKeyNavigableMap
        .computeIfAbsent(creationTime, ignored -> new LongArrayList())
        .add(recordKey);
  }

  private void handleJobRecord(
      final Record<?> record, final int partitionId, final long recordKey) {
    final var currentIntent = record.getIntent();

    if (currentIntent == JobIntent.CREATED) {
      storeJobCreation(record.getTimestamp(), recordKey);
    } else if (currentIntent == JobIntent.COMPLETED) {
      final var creationTime = jobKeyToCreationTimeMap.remove(recordKey);
      executionLatencyMetrics.observeJobLifeTime(partitionId, creationTime, record.getTimestamp());
    }
    executionLatencyMetrics.setCurrentJobsCount(partitionId, jobKeyToCreationTimeMap.size());
  }

  private void handleJobBatchRecord(final Record<?> record, final int partitionId) {
    final var currentIntent = record.getIntent();

    if (currentIntent == JobBatchIntent.ACTIVATED) {
      final var value = (JobBatchRecordValue) record.getValue();
      for (final long jobKey : value.getJobKeys()) {
        final var creationTime = jobKeyToCreationTimeMap.get(jobKey);
        executionLatencyMetrics.observeJobActivationTime(
            partitionId, creationTime, record.getTimestamp());
      }
    }
  }

  private void storeJobCreation(final long creationTime, final long recordKey) {
    jobKeyToCreationTimeMap.put(recordKey, creationTime);
    creationTimeToJobKeyNavigableMap
        .computeIfAbsent(creationTime, ignored -> new LongArrayList())
        .add(recordKey);
  }

  private void cleanUp() {
    final var currentTimeMillis = System.currentTimeMillis();

    final var deadTime = currentTimeMillis - TIME_TO_LIVE.toMillis();
    clearMaps(deadTime, creationTimeToJobKeyNavigableMap, jobKeyToCreationTimeMap);
    clearMaps(
        deadTime,
        creationTimeToProcessInstanceKeyNavigableMap,
        processInstanceKeyToCreationTimeMap);

    controller.scheduleCancellableTask(TIME_TO_LIVE, this::cleanUp);
  }

  @VisibleForTesting
  int cachedProcessInstanceCount() {
    return processInstanceKeyToCreationTimeMap.size();
  }

  @VisibleForTesting
  int cachedJobCount() {
    return jobKeyToCreationTimeMap.size();
  }

  private void clearMaps(
      final long deadTime,
      final NavigableMap<Long, LongArrayList> timeToKeyMap,
      final Long2LongHashMap keyToTimestampMap) {
    final var outOfScopeInstances = timeToKeyMap.headMap(deadTime);

    for (final LongArrayList keys : outOfScopeInstances.values()) {
      keys.forEach(keyToTimestampMap::remove);
    }
    outOfScopeInstances.clear();
  }

  public static ExporterCfg defaultConfig() {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(MetricsExporter.class.getName());
    return exporterCfg;
  }

  public static String defaultExporterId() {
    return MetricsExporter.class.getSimpleName();
  }

  private static boolean isProcessInstanceRecord(final Record<?> record) {
    final var recordValue = (ProcessInstanceRecordValue) record.getValue();
    return BpmnElementType.PROCESS == recordValue.getBpmnElementType();
  }
}
