import {createDurationFormattingOptions, getFormattedTargetValue} from './service';
import {formatTooltip, getTooltipLabelColor, canBeInterpolated} from '../service';
import {isDurationReport} from 'services';
import {getColorFor, createColors, determineBarColor} from '../colorsUtils';

export default function createDefaultChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, view, groupBy, configuration, decisionDefinitionKey},
    result,
    processInstanceCount,
    decisionInstanceCount
  } = report;

  const isDark = theme === 'dark';
  const instanceCountArr = [processInstanceCount || decisionInstanceCount || 0];
  const isDuration = isDurationReport(report);
  const maxValue = isDuration ? Math.max(...Object.values(result)) : 0;
  const isPersistedTooltips = isDuration
    ? configuration.alwaysShowAbsolute
    : configuration.alwaysShowAbsolute || configuration.alwaysShowRelative;

  let options;
  switch (visualization) {
    case 'pie':
      options = createPieOptions(isDark);
      break;
    case 'line':
    case 'bar':
      options = createBarOptions({
        targetValue,
        configuration,
        stacked: false,
        maxDuration: maxValue,
        isDark,
        isPersistedTooltips,
        autoSkip: canBeInterpolated(groupBy, configuration.xml, decisionDefinitionKey)
      });
      break;
    default:
      options = {};
  }

  return {
    ...options,
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    // plugin property
    showAllTooltips: isPersistedTooltips,
    tooltips: {
      ...(isPersistedTooltips && {
        yAlign: 'bottom',
        xAlign: 'center',
        displayColors: false
      }),
      callbacks: {
        ...(isPersistedTooltips && {title: () => ''}),
        // if pie chart then manually append labels to tooltips
        ...(visualization === 'pie' &&
          !isPersistedTooltips && {beforeLabel: ({index}, {labels}) => labels[index]}),

        label: (tooltipItem, data) => {
          return formatTooltip(
            tooltipItem,
            data,
            targetValue,
            configuration,
            formatter,
            instanceCountArr,
            view.property,
            visualization
          );
        },
        labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization)
      }
    }
  };
}

export function createBarOptions({
  targetValue,
  configuration,
  stacked,
  maxDuration,
  isDark,
  autoSkip,
  isPersistedTooltips
}) {
  const targetLine = targetValue && getFormattedTargetValue(targetValue);

  return {
    ...(configuration.pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    legend: {display: false},
    layout: {
      padding: {top: isPersistedTooltips ? 30 : 0}
    },
    scales: {
      yAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark)
          },
          scaleLabel: {
            display: !!configuration.yLabel,
            labelString: configuration.yLabel
          },
          ticks: {
            ...(maxDuration ? createDurationFormattingOptions(targetLine, maxDuration) : {}),
            beginAtZero: true,
            fontColor: getColorFor('label', isDark),
            suggestedMax: targetLine
          }
        }
      ],
      xAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark)
          },
          scaleLabel: {
            display: !!configuration.xLabel,
            labelString: configuration.xLabel
          },
          ticks: {
            fontColor: getColorFor('label', isDark),
            autoSkip
          },
          stacked
        }
      ]
    },
    // plugin property
    lineAt: targetLine
  };
}

function createPieOptions(isDark) {
  return {
    legend: {
      display: true,
      labels: {fontColor: getColorFor('label', isDark)}
    }
  };
}

export function createDatasetOptions(type, data, targetValue, datasetColor, isStriped, isDark) {
  switch (type) {
    case 'pie':
      return {
        borderColor: getColorFor('border', isDark),
        backgroundColor: createColors(Object.keys(data).length, isDark),
        borderWidth: undefined
      };
    case 'line':
      return {
        borderColor: datasetColor,
        backgroundColor: 'transparent',
        borderWidth: 2,
        legendColor: datasetColor
      };
    case 'bar':
    case 'number':
      const barColor = targetValue
        ? determineBarColor(targetValue, data, datasetColor, isStriped, isDark)
        : datasetColor;
      return {
        borderColor: barColor,
        backgroundColor: barColor,
        legendColor: datasetColor,
        borderWidth: 1
      };
    default:
      return {
        borderColor: undefined,
        backgroundColor: undefined,
        borderWidth: undefined
      };
  }
}
