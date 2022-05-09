/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useEffect, useCallback} from 'react';
import {Chart} from 'chart.js';

import {t} from 'translation';
import {formatters} from 'services';

import './DurationChart.scss';

const {createDurationFormattingOptions, duration} = formatters;

function DurationChart({data, colors}) {
  const canvas = useRef(null);

  const createTooltipTitle = useCallback(
    (tooltipData) => {
      if (!tooltipData.length) {
        return;
      }
      let key = 'common.instance';
      if (data[tooltipData[0].dataIndex].outlier) {
        key = 'analysis.outlier.tooltip.outlier';
      }

      const unitLabel = t(`${key}.label${+tooltipData[0].formattedValue !== 1 ? '-plural' : ''}`);

      return tooltipData[0].formattedValue + ' ' + unitLabel;
    },
    [data]
  );

  useEffect(() => {
    const maxDuration = data && data.length > 0 ? data[data.length - 1].key : 0;

    return new Chart(canvas.current, {
      type: 'bar',
      data: {
        labels: data.map(({key}) => key),
        datasets: [
          {
            data: data.map(({value}) => value),
            borderColor: colors,
            backgroundColor: colors,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            intersect: false,
            mode: 'x',
            callbacks: {
              title: createTooltipTitle,
              label: ({label, dataset, dataIndex}) => {
                const isSingleInstance = dataset.data[dataIndex] === 1;
                return ` ${t(
                  'analysis.outlier.tooltip.tookDuration.' +
                    (isSingleInstance ? 'singular' : 'plural')
                )} ${duration(label)}`;
              },
            },
            filter: (tooltipItem) => +tooltipItem.formattedValue > 0,
          },
        },
        scales: {
          xAxis: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.duration'),
              font: {weight: 'bold'},
            },
            ticks: {
              ...createDurationFormattingOptions(null, maxDuration),
            },
          },
          yAxis: {
            title: {
              display: true,
              text: t('analysis.outlier.detailsModal.axisLabels.instanceCount'),
              font: {weight: 'bold'},
            },
          },
        },
      },
    });
  }, [createTooltipTitle, data, colors]);

  return (
    <div className="DurationChart">
      <canvas ref={canvas} />
    </div>
  );
}

export default DurationChart;
