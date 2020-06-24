  /*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportConstants {

  // report data structure constants
  public static final String VIEW_RAW_DATA_OPERATION = "rawData";

  public static final String VIEW_FLOW_NODE_ENTITY = "flowNode";
  public static final String VIEW_USER_TASK_ENTITY = "userTask";
  public static final String VIEW_PROCESS_INSTANCE_ENTITY = "processInstance";
  public static final String VIEW_VARIABLE_ENTITY = "variable";

  public static final String VIEW_FREQUENCY_PROPERTY = "frequency";
  public static final String VIEW_DURATION_PROPERTY = "duration";
  public static final String VIEW_RAW_DATA_PROPERTY = "rawData";

  public static final String GROUP_BY_FLOW_NODES_TYPE = "flowNodes";
  public static final String GROUP_BY_USER_TASKS_TYPE = "userTasks";
  public static final String GROUP_BY_NONE_TYPE = "none";
  public static final String GROUP_BY_START_DATE_TYPE = "startDate";
  public static final String GROUP_BY_END_DATE_TYPE = "endDate";
  public static final String GROUP_BY_RUNNING_DATE_TYPE = "runningDate";
  public static final String GROUP_BY_VARIABLE_TYPE = "variable";
  public static final String GROUP_BY_ASSIGNEE = "assignee";
  public static final String GROUP_BY_CANDIDATE_GROUP = "candidateGroup";

  public static final String GROUP_BY_EVALUATION_DATE_TYPE = "evaluationDateTime";
  public static final String GROUP_BY_INPUT_VARIABLE_TYPE = "inputVariable";
  public static final String GROUP_BY_OUTPUT_VARIABLE_TYPE = "outputVariable";
  public static final String GROUP_BY_MATCHED_RULE_TYPE = "matchedRule";

  public static final String DATE_UNIT_YEAR = "year";
  public static final String DATE_UNIT_MONTH = "month";
  public static final String DATE_UNIT_WEEK = "week";
  public static final String DATE_UNIT_DAY = "day";
  public static final String DATE_UNIT_HOUR = "hour";
  public static final String DATE_UNIT_MINUTE = "minute";
  public static final String DATE_UNIT_AUTOMATIC = "automatic";

  // report configuration constants
  public static final String TABLE_VISUALIZATION = "table";
  public static final String HEAT_VISUALIZATION = "heat";
  public static final String SINGLE_NUMBER_VISUALIZATION = "number";
  public static final String BAR_VISUALIZATION = "bar";
  public static final String LINE_VISUALIZATION = "line";
  public static final String BADGE_VISUALIZATION = "badge";
  public static final String PIE_VISUALIZATION = "pie";

  public static final String DEFAULT_CONFIGURATION_COLOR = "#1991c8";

  public static final String AVERAGE_AGGREGATION_TYPE = "avg";
  public static final String MIN_AGGREGATION_TYPE = "min";
  public static final String MAX_AGGREGATION_TYPE = "max";
  public static final String MEDIAN_AGGREGATION_TYPE = "median";
  public static final String SUM_AGGREGATION_TYPE = "sum";

  public static final String DISTRIBUTED_BY_NONE = "none";
  public static final String DISTRIBUTED_BY_USER_TASK = "userTask";
  public static final String DISTRIBUTED_BY_ASSIGNEE = "assignee";
  public static final String DISTRIBUTED_BY_CANDIDATE_GROUP = "candidateGroup";

  public static final String RUNNING_FLOWNODE_EXECUTION_STATE = "running";
  public static final String COMPLETED_FLOWNODE_EXECUTION_STATE = "completed";
  public static final String ALL_FLOWNODE_EXECUTION_STATE = "all";

  public static final String IDLE_USER_TASK_DURATION_TIME = "idle";
  public static final String WORK_USER_TASK_DURATION_TIME = "work";
  public static final String TOTAL_USER_TASK_DURATION_TIME = "total";

  // alert constants
  public static final String ALERT_THRESHOLD_OPERATOR_GREATER = ">";
  public static final String ALERT_THRESHOLD_OPERATOR_LESS = "<";

  // miscellaneous report constants
  public static final String ALL_VERSIONS = "ALL";
  public static final String LATEST_VERSION = "LATEST";

  public static final String FIXED_DATE_FILTER = "fixed";
  public static final String RELATIVE_DATE_FILTER = "relative";
  public static final String ROLLING_DATE_FILTER = "rolling";

  public static final String RAW_RESULT_TYPE = "raw";
  public static final String NUMBER_RESULT_TYPE = "number";
  public static final String MAP_RESULT_TYPE = "map";
  public static final String HYPER_MAP_RESULT_TYPE = "hyperMap";

  public static final String MISSING_VARIABLE_KEY = "missing";

  // variable type constants
  // first letter uppercase is used by VariableFilterDataDto json type info
  public static final String STRING_TYPE = "String";
  public static final String STRING_TYPE_LOWERCASE = "string";
  public static final String INTEGER_TYPE = "Integer";
  public static final String INTEGER_TYPE_LOWERCASE = "integer";
  public static final String SHORT_TYPE = "Short";
  public static final String SHORT_TYPE_LOWERCASE = "short";
  public static final String LONG_TYPE = "Long";
  public static final String LONG_TYPE_LOWERCASE = "long";
  public static final String DOUBLE_TYPE = "Double";
  public static final String DOUBLE_TYPE_LOWERCASE = "double";
  public static final String BOOLEAN_TYPE = "Boolean";
  public static final String BOOLEAN_TYPE_LOWERCASE = "boolean";
  public static final String DATE_TYPE = "Date";
  public static final String DATE_TYPE_LOWERCASE = "date";

  public static final VariableType[] ALL_SUPPORTED_VARIABLE_TYPES = VariableType.values();

  // A report result can have three states in theory for duration reports:
  // * an arbitrary positive value,
  // * zero duration
  // * no data available
  // To differentiate between an activity/process instance took 0ms and no data available the
  // null result indicates that there's no data.
  public static final Double NO_DATA_AVAILABLE_RESULT = null;
}
