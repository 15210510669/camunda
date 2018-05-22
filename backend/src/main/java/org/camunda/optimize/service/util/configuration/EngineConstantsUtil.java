package org.camunda.optimize.service.util.configuration;

public class EngineConstantsUtil {

  public static final String MAX_RESULTS_TO_RETURN = "maxResults";
  public static final String INDEX_OF_FIRST_RESULT = "firstResult";

  public static final String INCLUDE_PROCESS_INSTANCE_IDS = "processInstanceIds";
  public static final String INCLUDE_PROCESS_INSTANCE_ID_IN = "processInstanceIdIn";
  public static final String INCLUDE_VARIABLE_TYPE_IN = "variableTypeIn";

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String ID = "id";

  public static final String FINISHED_AFTER = "finishedAfter";
  public static final String FINISHED_BEFORE = "finishedBefore";

  public static final String STARTED_AFTER = "startedAfter";
  public static final String STARTED_BEFORE = "startedBefore";

  public static final String LATEST_VERSION = "latestVersion";
  public static final String INCLUDE_ONLY_FINISHED_INSTANCES = "finished";
  public static final String INCLUDE_ONLY_UNFINISHED_INSTANCES = "unfinished";
  public static final String TRUE = "true";

  public static final String SORT_BY = "sortBy";
  public static final String SORT_TYPE_START_TIME = "startTime";
  public static final String SORT_TYPE_END_TIME = "endTime";
  public static final String SORT_TYPE_ID = "id";
  public static final String SORT_BY_TIME = "time";


  public static final String SORT_ORDER = "sortOrder";
  public static final String SORT_ORDER_TYPE_ASCENDING = "asc";
  public static final String SORT_ORDER_TYPE_DESCENDING = "desc";

  public static final String OCCURRED_AFTER = "occurredAfter";
  public static final String OCCURRED_BEFORE = "occurredBefore";
  public static final String DESERIALIZE_VALUES = "deserializeValues";
  public static final String VARIABLE_UPDATES = "variableUpdates";

  public static final String MEMBER = "member";

  public static final String HISTORY_DETAIL_ENDPOINT = "/history/detail";
  public static final String AUTHORIZATION_ENDPOINT = "/authorization";
  public static final String GROUP_ENDPOINT = "/group";

  public static final String ALL_PERMISSION = "ALL";
  public static final String ACCESS_PERMISSION = "ACCESS";
  public static final String READ_HISTORY_PERMISSION = "READ_HISTORY";

  public static final String RESOURCE_TYPE = "resourceType";
  public static final int RESOURCE_TYPE_APPLICATION = 0;
  public static final int RESOURCE_TYPE_PROCESS_DEFINITION = 6;

  public static final int AUTHORIZATION_TYPE_GLOBAL = 0;
  public static final int AUTHORIZATION_TYPE_GRANT = 1;
  public static final int AUTHORIZATION_TYPE_REVOKE = 2;


  public static final String OPTIMIZE_APPLICATION_RESOURCE_ID = "optimize";
  public static final String ALL_RESOURCES_RESOURCE_ID = "*";

}
