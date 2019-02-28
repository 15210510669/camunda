package org.camunda.optimize.service.util.configuration;

public class EngineConstantsUtil {

  public static final String MAX_RESULTS_TO_RETURN = "maxResults";
  public static final String INDEX_OF_FIRST_RESULT = "firstResult";

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String ID = "id";

  public static final String FINISHED_AFTER = "finishedAfter";
  public static final String FINISHED_AT = "finishedAt";

  public static final String STARTED_AFTER = "startedAfter";
  public static final String STARTED_AT = "startedAt";

  public static final String OCCURRED_AFTER = "occurredAfter";
  public static final String OCCURRED_AT = "occurredAt";

  public static final String MEMBER = "member";

  public static final String VARIABLE_UPDATE_ENDPOINT = "/optimize/variable-update";
  public static final String COMPLETED_ACTIVITY_INSTANCE_ENDPOINT = "/optimize/activity-instance/completed";
  public static final String RUNNING_ACTIVITY_INSTANCE_ENDPOINT = "/optimize/activity-instance/running";
  public static final String COMPLETED_PROCESS_INSTANCE_ENDPOINT = "/optimize/process-instance/completed";
  public static final String RUNNING_PROCESS_INSTANCE_ENDPOINT = "/optimize/process-instance/running";
  public static final String COMPLETED_USER_TASK_INSTANCE_ENDPOINT = "/optimize/task-instance/completed";
  public static final String USER_OPERATIONS_LOG_ENDPOINT = "/optimize/user-operation";

  public static final String DECISION_INSTANCE_ENDPOINT = "/optimize/decision-instance";

  public static final String AUTHORIZATION_ENDPOINT = "/authorization";
  public static final String GROUP_ENDPOINT = "/group";

  public static final String ALL_PERMISSION = "ALL";
  public static final String ACCESS_PERMISSION = "ACCESS";
  public static final String READ_HISTORY_PERMISSION = "READ_HISTORY";
  public static final String READ_PERMISSION = "READ";

  public static final String RESOURCE_TYPE = "resourceType";
  public static final int RESOURCE_TYPE_APPLICATION = 0;
  public static final int RESOURCE_TYPE_PROCESS_DEFINITION = 6;
  public static final int RESOURCE_TYPE_DECISION_DEFINITION = 10;
  public static final int RESOURCE_TYPE_USER = 1;
  public static final int RESOURCE_TYPE_AUTHORIZATION = 4;

  public static final int AUTHORIZATION_TYPE_GLOBAL = 0;
  public static final int AUTHORIZATION_TYPE_GRANT = 1;
  public static final int AUTHORIZATION_TYPE_REVOKE = 2;


  public static final String OPTIMIZE_APPLICATION_RESOURCE_ID = "optimize";
  public static final String ALL_RESOURCES_RESOURCE_ID = "*";

}
