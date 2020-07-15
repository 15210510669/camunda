/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';

const {formatReportResult} = formatters;

// Override the default generate legend's labels function
// This is done to modify the colors retrieval method of the legend squares and filter unneeded labels
export function generateLegendLabels(chart) {
  const data = chart.data;
  return data.datasets.length
    ? data.datasets
        .map(function (dataset) {
          return {
            text: dataset.label,
            fillStyle: !dataset.backgroundColor.length
              ? dataset.backgroundColor
              : dataset.legendColor,
            strokeStyle: dataset.legendColor,
          };
        }, this)
        .filter((dataset) => {
          return dataset.text;
        })
    : [];
}

export function getCombinedChartProps(reports, data) {
  return data.reports.reduce(
    (prev, {id, color}) => {
      const report = reports[id];
      // skip unauthorized reports
      if (!report) {
        return prev;
      }
      let singleReportResult;
      if (data.visualization === 'number') {
        singleReportResult = [{key: report.name, value: report.result.data}];
      } else {
        singleReportResult = formatReportResult(data, report.result.data);
      }

      return {
        resultArr: [...prev.resultArr, singleReportResult],
        reportsNames: [...prev.reportsNames, report.name],
        reportColors: [...prev.reportColors, color],
      };
    },
    {resultArr: [], reportsNames: [], reportColors: []}
  );
}
