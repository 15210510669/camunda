/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';

type Tasks = ReadonlyArray<
  Pick<
    Task,
    | 'id'
    | 'name'
    | 'assignee'
    | 'processName'
    | 'creationTime'
    | 'taskState'
    | 'sortValues'
    | 'isFirst'
  >
>;

const getSortValues = (tasks?: Tasks) => {
  if (tasks !== undefined && tasks?.length > 0 && !tasks[0].isFirst) {
    return tasks[0].sortValues;
  }

  return undefined;
};

export {getSortValues};
