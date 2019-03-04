/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

const URL = '/api/activity-instances';

export async function fetchActivityInstancesTree(workflowInstanceId) {
  const response = await post(URL, {
    workflowInstanceId
  });
  return await response.json();
}
