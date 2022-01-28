/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';

async function fetchInstancesByProcess() {
  return request({url: '/api/incidents/byProcess'});
}

async function fetchIncidentsByError() {
  return request({url: '/api/incidents/byError'});
}

export {fetchInstancesByProcess, fetchIncidentsByError};
