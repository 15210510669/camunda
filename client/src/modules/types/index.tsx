/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type NonEmptyArray<T> = [T, ...T[]];

type Permissions = NonEmptyArray<'read' | 'write'>;

type CurrentUser = {
  userId: string;
  displayName: string | null;
  permissions: Permissions;
  roles: string[] | null;
  salesPlanType: string | null;
  c8Links: {
    name: 'console' | 'modeler' | 'tasklist' | 'operate' | 'optimize';
    link: string;
  }[];
};

type Variable = {
  id: string;
  name: string;
  value: string;
  previewValue: string;
  isValueTruncated: boolean;
};

type TaskState = 'CREATED' | 'COMPLETED' | 'CANCELED';

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationDate: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionDate: string | null;
  assignee: string | null;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[];
  candidateUsers: string[];
};

type Form = {
  id: string;
  processDefinitionKey: string;
  schema: string;
  title: string;
};

type Process = {
  id: string;
  name: string | null;
  bpmnProcessId: string;
  version: number;
  startEventFormId: string | null;
  sortValues: [string];
};

type ProcessInstance = {
  id: string;
};

type DateSearch = {
  from: string;
  to: string;
};

type PaginationSearchPair = [string, string];

type TasksSearchBody = {
  state?: TaskState;
  followUpDate?: DateSearch;
  dueDate?: DateSearch;
  assigned?: boolean;
  assignee?: string;
  taskDefinitionId?: string;
  candidateGroup?: string;
  candidateUser?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  pageSize?: number;
  sort?: Array<{
    field: 'completionTime' | 'creationTime' | 'followUpDate' | 'dueDate';
    order: 'ASC' | 'DESC';
  }>;
  searchAfter?: PaginationSearchPair;
  searchAfterOrEqual?: PaginationSearchPair;
  searchBefore?: PaginationSearchPair;
  searchBeforeOrEqual?: PaginationSearchPair;
};

export type {
  CurrentUser,
  Variable,
  Task,
  TaskState,
  Form,
  Permissions,
  Process,
  ProcessInstance,
  TasksSearchBody,
};
