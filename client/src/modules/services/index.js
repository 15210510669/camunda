/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as formattersImport from './formatters';

export {
  getFlowNodeNames,
  loadDefinitions,
  loadProcessDefinitionXml,
  loadDecisionDefinitionXml,
  checkDeleteConflict,
  loadVariables,
  loadInputVariables,
  loadOutputVariables,
} from './dataLoaders';
export {numberParser} from './NumberParser';
export {incompatibleFilters} from './incompatibleFilters';
export {containsSuspensionFilter} from './suspensionFilter';
export {default as reportConfig} from './reportConfig';
export {getDiagramElementsBetween} from './diagramServices';
export {flatten} from './tableServices';
export {default as getTooltipText} from './getTooltipText';
export {
  loadEntity,
  loadReports,
  createEntity,
  updateEntity,
  deleteEntity,
  copyReport,
} from './entityService';

export {evaluateReport, isDurationReport, loadRawData} from './reportService';

// unfortunately, there is no syntax like "export * as formatters from './formatters'"
export const formatters = formattersImport;

export function getRandomId() {
  return Math.random().toString(36).slice(2);
}

export function capitalize(string) {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

export function getCollection(path) {
  const collectionMatch = /\/collection\/([^/]+)/g.exec(path);
  return collectionMatch && collectionMatch[1];
}
