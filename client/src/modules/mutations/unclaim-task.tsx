/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {unclaimedTask} from 'modules/mock-schema/mocks/task-details';

import {Task} from 'modules/types';

type UnclaimTaskVariables = {
  id: Task['id'];
};

const UNCLAIM_TASK = gql`
  mutation UnclaimTask($id: String!) {
    unclaimTask(taskId: $id) {
      id
      assignee {
        username
        firstname
        lastname
      }
    }
  }
`;

const mockUnclaimTask = {
  request: {
    query: UNCLAIM_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      unclaimTask: unclaimedTask,
    },
  },
};

export type {UnclaimTaskVariables};
export {UNCLAIM_TASK, mockUnclaimTask};
