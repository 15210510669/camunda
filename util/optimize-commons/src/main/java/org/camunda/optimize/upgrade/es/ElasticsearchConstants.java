/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

public class ElasticsearchConstants {

  // Note: we cap listings to 1000 as a generous practical limit, no paging
  public static final int LIST_FETCH_LIMIT = 1000;

  public static final int MAX_RESPONSE_SIZE_LIMIT = 10_000;

  public static final int NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION = 80;

  public static final int NUMBER_OF_RETRIES_ON_CONFLICT = 5;

  public static final String OPTIMIZE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  public static final String NUMBER_OF_REPLICAS_SETTING = "number_of_replicas";
  public static final String NUMBER_OF_SHARDS_SETTING = "number_of_shards";
  public static final String REFRESH_INTERVAL_SETTING = "refresh_interval";
  public static final String ANALYSIS_SETTING = "analysis";
  public static final String SORT_SETTING = "sort";
  public static final String SORT_FIELD_SETTING = "field";
  public static final String SORT_ORDER_SETTING = "order";

  public static final String DECISION_DEFINITION_INDEX_NAME = "decision-definition";
  public static final String DECISION_INSTANCE_INDEX_NAME = "decision-instance";

  public static final String SINGLE_PROCESS_REPORT_INDEX_NAME = "single-process-report";
  public static final String SINGLE_DECISION_REPORT_INDEX_NAME = "single-decision-report";
  public static final String COMBINED_REPORT_INDEX_NAME = "combined-report";
  public static final String DASHBOARD_INDEX_NAME = "dashboard";
  public static final String COLLECTION_INDEX_NAME = "collection";
  public static final String PROCESS_DEFINITION_INDEX_NAME = "process-definition";
  public static final String PROCESS_INSTANCE_INDEX_NAME = "process-instance";
  public static final String IMPORT_INDEX_INDEX_NAME = "import-index";
  public static final String LICENSE_INDEX_NAME = "license";
  public static final String ALERT_INDEX_NAME = "alert";
  public static final String REPORT_SHARE_INDEX_NAME = "report-share";
  public static final String DASHBOARD_SHARE_INDEX_NAME = "dashboard-share";
  public static final String TIMESTAMP_BASED_IMPORT_INDEX_NAME = "timestamp-based-import-index";
  public static final String METADATA_INDEX_NAME = "metadata";
  public static final String TERMINATED_USER_SESSION_INDEX_NAME = "terminated-user-session";
  public static final String TENANT_INDEX_NAME = "tenant";
  public static final String EVENT_INDEX_NAME = "event";
  public static final String EVENT_PROCESS_MAPPING_INDEX_NAME = "event-process-mapping";
  public static final String EVENT_SEQUENCE_COUNT_INDEX_NAME = "event-sequence-count";
  public static final String EVENT_TRACE_STATE_INDEX_NAME = "event-trace-state";
  public static final String EVENT_PROCESS_DEFINITION_INDEX_NAME = "event-process-definition";
  public static final String EVENT_PROCESS_PUBLISH_STATE_INDEX = "event-process-publish-state";
  public static final String ONBOARDING_INDEX_NAME = "onboarding-state";

  public static final String EVENT_PROCESS_INSTANCE_INDEX_PREFIX = "event-process-instance-";
  public static final String CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX = "camunda-activity-event-";

  public static final String INDEX_SUFFIX_PRE_ROLLOVER = "-000001";

  public static final String EVENT_PROCESSING_IMPORT_REFERENCE = "eventStateProcessing";
  public static final String EVENT_PROCESSING_ENGINE_REFERENCE = "optimize";

  public static final String METADATA_TYPE_SCHEMA_VERSION = "schemaVersion";
}
