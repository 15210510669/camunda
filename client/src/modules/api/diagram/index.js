/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'modules/request';

export const fetchWorkflowXML = async (workflowId) => {
  const response = await get(`/api/workflows/${workflowId}/xml`);
  return await response.text();
};
