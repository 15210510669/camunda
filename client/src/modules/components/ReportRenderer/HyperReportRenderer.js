/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ColorPicker} from 'components';
import {formatters} from 'services';

import CombinedReportRenderer from './CombinedReportRenderer';

const {formatReportResult} = formatters;

export default function HyperReportRenderer({report, ...rest}) {
  const convertedReport = {
    ...report,
  };

  const firstEntryResult = report.result.data[0].value.filter(isVisible(report));

  const colors = ColorPicker.getColors(firstEntryResult.length);

  convertedReport.combined = true;
  convertedReport.data = {
    configuration: report.data.configuration,
    reports: firstEntryResult.map(({key}, i) => ({id: key, color: colors[i]})),
    visualization: getVisualization(report.data.visualization),
  };

  const newResultData = {};

  formatResult(report.data, firstEntryResult).forEach(({key, label}) => {
    newResultData[key] = {
      combined: false,
      id: key,
      name: label,
      reportType: 'process',
      data: report.data,
      result: {
        ...report.result,
        type: 'map',
        data: report.result.data.map((entry) => ({
          ...entry,
          value: entry.value.find((data) => data.key === key).value,
        })),
      },
    };
  });

  convertedReport.result = {
    ...report.result,
    type: null,
    data: newResultData,
  };

  return <CombinedReportRenderer {...rest} report={convertedReport} />;
}

function getVisualization(visualization) {
  if (['table', 'line'].includes(visualization)) {
    return visualization;
  }
  return 'bar';
}

function formatResult(data, result) {
  const {
    configuration: {distributedBy, distributeByDateVariableUnit},
  } = data;

  if (distributedBy.type === 'variable' && distributedBy.value.type === 'Date') {
    return formatReportResult(
      {
        ...data,
        groupBy: distributedBy,
        configuration: {
          ...data.configuration,
          groupByDateVariableUnit: distributeByDateVariableUnit,
        },
      },
      result
    );
  }

  return result;
}

function isVisible(report) {
  const {distributedBy, hiddenNodes} = report.data.configuration;

  return ({key}) => {
    if (
      ['flowNode', 'userTask'].includes(distributedBy.type) &&
      hiddenNodes.active &&
      hiddenNodes.keys.includes(key)
    ) {
      return false;
    }

    return true;
  };
}
