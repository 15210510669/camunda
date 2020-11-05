/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

export function concatTitle(
  workflowName: any,
  instancesCount: any,
  versionName: any,
  errorMessage: any
) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} with error ${errorMessage} in version ${versionName} of Workflow ${workflowName}`;
}

export function concatLabel(name: any, version: any) {
  return `${name} – Version ${version}`;
}

export function concatGroupTitle(
  instancesWithErrorCount: any,
  errorMessage: any
) {
  return `View ${pluralSuffix(
    instancesWithErrorCount,
    'Instance'
  )} with error ${errorMessage}`;
}

export function concatButtonTitle(
  instancesWithErrorCount: any,
  errorMessage: any
) {
  return `Expand ${pluralSuffix(
    instancesWithErrorCount,
    'Instance'
  )} with error ${errorMessage}`;
}
