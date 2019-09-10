/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getColorFor} from './colorsUtils';
import {getTooltipText} from 'services';

export function formatTooltip(
  {index, datasetIndex},
  {datasets},
  targetValue,
  {alwaysShowAbsolute, alwaysShowRelative},
  formatter,
  processInstanceCountData = [],
  hideRelative,
  type
) {
  if (datasets[datasetIndex].isTarget) {
    return;
  }

  let processInstanceCount = processInstanceCountData[datasetIndex];
  // in the case of the line with target value we have 2 datasets for each report
  // we have to divide by 2 to get the right index
  if (type === 'line' && targetValue) {
    processInstanceCount = processInstanceCountData[~~(datasetIndex / 2)];
  }

  return getTooltipText(
    datasets[datasetIndex].data[index],
    formatter,
    processInstanceCount,
    alwaysShowAbsolute,
    alwaysShowRelative,
    hideRelative
  );
}

export function getTooltipLabelColor(tooltipItem, chart, type) {
  const datasetOptions = chart.data.datasets[tooltipItem.datasetIndex];
  if (type === 'pie') {
    const color = datasetOptions.backgroundColor[tooltipItem.index];
    return {
      borderColor: color,
      backgroundColor: color
    };
  }

  return {
    borderColor: datasetOptions.legendColor,
    backgroundColor: datasetOptions.legendColor
  };
}

export function drawHorizentalLine(chart) {
  if (chart.options.lineAt >= 0 && chart.options.lineAt !== false) {
    const ctx = chart.chart.ctx;
    const xAxe = chart.scales[chart.options.scales.xAxes[0].id];
    const lineAt = calculateLinePosition(chart);

    ctx.save();
    ctx.strokeStyle = getColorFor('targetBar', true);
    ctx.setLineDash([10, 10]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xAxe.left, lineAt);
    ctx.lineTo(xAxe.right, lineAt);
    ctx.stroke();
    ctx.restore();
  }
}

export function calculateLinePosition(chart) {
  const yAxis = chart.scales[chart.options.scales.yAxes[0].id];

  return (1 - chart.options.lineAt / yAxis.max) * yAxis.height + yAxis.top;
}

export function canBeInterpolated({type, value}, xml, decisionDefinitionKey) {
  if (type === 'flowNodes') {
    return false;
  }
  if (type === 'variable' && value.type === 'String') {
    return false;
  }
  if (type === 'inputVariable' || type === 'outputVariable') {
    return (
      new DOMParser()
        .parseFromString(xml, 'text/xml')
        .querySelector(
          `decision[id="${decisionDefinitionKey}"] [id="${value.id}"] ${
            type === 'inputVariable' ? 'inputExpression' : ''
          }`
        )
        .getAttribute('typeRef')
        .toLowerCase() !== 'string'
    );
  }
  return true;
}
