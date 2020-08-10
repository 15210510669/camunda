/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseISO} from 'date-fns';

import {format} from 'dates';
import {formatters} from 'services';
import {t} from 'translation';

import {sortColumns, cockpitLink, getNoDataMessage, isVisibleColumn} from './service';

const {duration} = formatters;

export default function processRawData(
  {
    report: {
      data: {
        configuration: {
          tableColumns,
          columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []},
        },
      },
      result: {data: result},
    },
  },
  endpoints = {}
) {
  const instanceProps = Object.keys(result[0]).filter(
    (entry) => entry !== 'variables' && isVisibleColumn(entry, tableColumns)
  );
  const variableNames = Object.keys(result[0].variables).filter((entry) =>
    isVisibleColumn('variable:' + entry, tableColumns)
  );

  // If all columns are excluded return a message to enable one
  if (instanceProps.length + variableNames.length === 0) {
    return getNoDataMessage();
  }

  const body = result.map((instance) => {
    const row = instanceProps.map((entry) => {
      if (entry === 'processInstanceId') {
        return cockpitLink(endpoints, instance, 'process');
      }
      if ((entry === 'startDate' || entry === 'endDate') && instance[entry]) {
        return format(parseISO(instance[entry]), "yyyy-MM-dd HH:mm:ss 'UTC'X");
      }
      if (entry === 'duration') {
        return duration(instance[entry]);
      }
      return instance[entry];
    });
    const variableValues = variableNames.map((entry) => {
      const value = instance.variables[entry];
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    row.push(...variableValues);

    return row;
  });

  const head = instanceProps.map((key) => t('report.table.rawData.' + key));

  if (variableNames.length > 0) {
    head.push({label: t('report.variables.default'), columns: variableNames});
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
