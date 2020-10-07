/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';

import {default as reportConfig} from './reportConfig';
import * as decisionOptions from './decision';
import * as processOptions from './process';

const config = {
  process: reportConfig(processOptions),
  decision: reportConfig(decisionOptions),
};

const processUpdate = config.process.update;
config.process.update = (type, data, props) => {
  const changes = processUpdate(type, data, props);

  changes.configuration = changes.configuration || {};
  changes.configuration.sorting = {$set: getDefaultSorting(update(props.report, {data: changes}))};

  if (type === 'view') {
    changes.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};

    if (data.entity !== 'variable' && props.report.data.configuration?.aggregationType === 'sum') {
      changes.configuration.aggregationType = {$set: 'avg'};
    }

    if (data.property !== 'duration' || data.entity !== 'processInstance') {
      changes.configuration.processPart = {$set: null};
    }

    if (data.entity === 'userTask' && props.report.data.view?.entity !== 'userTask') {
      changes.configuration.hiddenNodes = {$set: {active: false, keys: []}};
    }
  }

  if (shouldResetDistributedBy(type, data, props.report.data)) {
    changes.configuration.distributedBy = {$set: {type: 'none', value: null}};
  }

  return changes;
};

const decisionUpdate = config.decision.update;
config.decision.update = (type, data, props) => {
  const changes = decisionUpdate(type, data, props);
  changes.configuration = changes.configuration || {};
  changes.configuration.sorting = {$set: getDefaultSorting(update(props.report, {data: changes}))};

  return changes;
};

function shouldResetDistributedBy(type, data, report) {
  if (report.view?.entity === 'flowNode') {
    // flow node reports: reset when changing from date for flow node grouping
    if (type === 'groupBy' && data.type === 'flowNodes') {
      return true;
    }

    // flow node reports: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'flowNode') {
      return true;
    }
  }

  if (report.view?.entity === 'userTask') {
    // user task report: reset when it's distributed by usertask and we switch to group by usertask
    if (
      type === 'groupBy' &&
      data.type === 'userTasks' &&
      report.configuration?.distributedBy.type === 'userTask'
    ) {
      return true;
    }

    // user task report: reset when it's distributed by assignee and we switch to group by assignee
    if (
      type === 'groupBy' &&
      ['assignee', 'candidateGroup'].includes(data.type) &&
      ['assignee', 'candidateGroup'].includes(report.configuration?.distributedBy.type)
    ) {
      return true;
    }

    // user task report: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'userTask') {
      return true;
    }
  }

  if (report.view?.entity === 'processInstance') {
    // process instance reports: reset when it's distributed by variable and we switch from start/end date to any other grouping
    if (type === 'groupBy') {
      if (
        report.configuration?.distributedBy.type === 'variable' &&
        data.type !== 'startDate' &&
        data.type !== 'endDate'
      ) {
        return true;
      }

      // process instance reports: reset when it's distributed by start/end date and we switch from variable to any other grouping
      if (
        ['startDate', 'endDate'].includes(report.configuration?.distributedBy.type) &&
        data.type !== 'variable'
      ) {
        return true;
      }
    }

    // process instance reports: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'processInstance') {
      return true;
    }
  }

  return false;
}

function getDefaultSorting({reportType, data: {view, groupBy, visualization}}) {
  if (visualization !== 'table') {
    return null;
  }

  if (view?.property === 'rawData') {
    const by = reportType === 'process' ? 'startDate' : 'evaluationDateTime';
    return {by, order: 'desc'};
  }

  if (['flowNodes', 'userTasks'].includes(groupBy?.type)) {
    return {by: 'label', order: 'asc'};
  }

  if (groupBy?.type.toLowerCase().includes('variable')) {
    // Descending for Date and Boolean
    // Ascending for Integer, Double, Long, Date
    const order = ['Date', 'Boolean'].includes(groupBy.value.type) ? 'desc' : 'asc';
    return {by: 'key', order};
  }

  return {by: 'key', order: 'desc'};
}

export default config;
