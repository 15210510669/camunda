/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {key: 'rawData', group: 'raw', data: {property: 'rawData', entity: null}},
  {
    key: 'pi',
    options: [
      {
        key: 'pi_count',
        group: 'pi_count',
        data: {property: 'frequency', entity: 'processInstance'},
      },
      {
        key: 'pi_duration',
        group: 'pi_duration',
        data: {property: 'duration', entity: 'processInstance'},
      },
    ],
  },
  {
    key: 'fn',
    group: 'fn',
    options: [
      {
        key: 'fn_count',

        data: {property: 'frequency', entity: 'flowNode'},
      },
      {key: 'fn_duration', data: {property: 'duration', entity: 'flowNode'}},
    ],
  },
  {
    key: 'userTask',
    group: 'userTask',
    options: [
      {
        key: 'userTask_count',

        data: {property: 'frequency', entity: 'userTask'},
      },
      {
        key: 'userTask_duration',

        data: {property: 'duration', entity: 'userTask'},
      },
    ],
  },
];

export const groupBy = [
  {key: 'none', group: 'none', data: {type: 'none', value: null}},
  {key: 'flowNodes', group: 'fn', data: {type: 'flowNodes', value: null}},
  {key: 'userTasks', group: 'task', data: {type: 'userTasks', value: null}},
  {
    key: 'startDate',
    group: 'date',
    options: [
      {
        key: 'startDate_automatic',

        data: {type: 'startDate', value: {unit: 'automatic'}},
      },
      {key: 'startDate_year', data: {type: 'startDate', value: {unit: 'year'}}},
      {key: 'startDate_month', data: {type: 'startDate', value: {unit: 'month'}}},
      {key: 'startDate_week', data: {type: 'startDate', value: {unit: 'week'}}},
      {key: 'startDate_day', data: {type: 'startDate', value: {unit: 'day'}}},
      {key: 'startDate_hour', data: {type: 'startDate', value: {unit: 'hour'}}},
    ],
  },
  {
    key: 'runningDate',
    group: 'runningDate',
    options: [
      {
        key: 'runningDate_automatic',

        data: {type: 'runningDate', value: {unit: 'automatic'}},
      },
      {key: 'runningDate_year', data: {type: 'runningDate', value: {unit: 'year'}}},
      {key: 'runningDate_month', data: {type: 'runningDate', value: {unit: 'month'}}},
      {key: 'runningDate_week', data: {type: 'runningDate', value: {unit: 'week'}}},
      {key: 'runningDate_day', data: {type: 'runningDate', value: {unit: 'day'}}},
      {key: 'runningDate_hour', data: {type: 'runningDate', value: {unit: 'hour'}}},
    ],
  },
  {
    key: 'endDate',
    group: 'date',
    options: [
      {
        key: 'endDate_automatic',

        data: {type: 'endDate', value: {unit: 'automatic'}},
      },
      {key: 'endDate_year', data: {type: 'endDate', value: {unit: 'year'}}},
      {key: 'endDate_month', data: {type: 'endDate', value: {unit: 'month'}}},
      {key: 'endDate_week', data: {type: 'endDate', value: {unit: 'week'}}},
      {key: 'endDate_day', data: {type: 'endDate', value: {unit: 'day'}}},
      {key: 'endDate_hour', data: {type: 'endDate', value: {unit: 'hour'}}},
    ],
  },
  {key: 'variable', group: 'variable', options: 'variable'},
  {key: 'userAssignee', group: 'user', data: {type: 'assignee', value: null}},
  {
    key: 'userGroup',
    group: 'user',
    data: {type: 'candidateGroup', value: null},
  },
];

export const visualization = [
  {key: 'number', group: 'number', data: 'number'},
  {key: 'table', group: 'table', data: 'table'},
  {key: 'bar', group: 'chart', data: 'bar'},
  {key: 'line', group: 'chart', data: 'line'},
  {key: 'pie', group: 'chart', data: 'pie'},
  {key: 'heat', group: 'heat', data: 'heat'},
];

export const combinations = {
  raw: {
    none: ['table'],
  },
  pi_duration: {
    none: ['number'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  pi_count: {
    none: ['number'],
    date: ['table', 'chart'],
    runningDate: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  fn: {
    fn: ['table', 'chart', 'heat'],
  },
  userTask: {
    task: ['table', 'chart', 'heat'],
    date: ['table', 'chart'],
    user: ['table', 'chart'],
  },
};
