/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export function isDurationReport(report) {
  // waiting for optional chaining... https://github.com/tc39/proposal-optional-chaining
  return report && report.data && report.data.view && report.data.view.property === 'duration';
}

export async function evaluateReport(query, filter = []) {
  let response;

  if (typeof query !== 'object') {
    // evaluate saved report
    response = await post(`api/report/${query}/evaluate`, {filter});
  } else {
    // evaluate unsaved report
    response = await post(`api/report/evaluate/`, query);
  }

  return await response.json();
}
