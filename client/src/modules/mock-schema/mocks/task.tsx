/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from 'modules/mock-schema/mocks/current-user';

const unclaimedTask = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const unclaimedTaskWithForm = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const completedTask = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.username,
  creationTime: new Date('2019').toISOString(),
  completionTime: new Date('2020').toISOString(),
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const completedTaskWithForm = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.username,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const claimedTask = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.username,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const claimedTaskWithForm = (id = '0'): Omit<Task, 'variables'> => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.username,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

export {
  unclaimedTask,
  completedTask,
  claimedTask,
  unclaimedTaskWithForm,
  claimedTaskWithForm,
  completedTaskWithForm,
};
