import moment from 'moment';

import {drawHorizentalLine} from './service';
import zoomIn from './zoomIn';
import showAllTooltips from './showAllTooltips';

export default function createPlugins({updateReport, report: {combined, data, result}}) {
  const plugins = [
    {
      afterDatasetsDraw: drawHorizentalLine
    },
    showAllTooltips
  ];

  if (
    !combined &&
    updateReport &&
    ['startDate', 'evaluationDateTime'].includes(data.groupBy.type) &&
    ['line', 'bar'].includes(data.visualization)
  ) {
    const dataPoints = Object.keys(result).sort();

    plugins.push(
      zoomIn({
        updateReport,
        filters: data.filter,
        type: data.groupBy.type,
        valueRange: {
          min: moment(dataPoints[0]),
          max: moment(dataPoints[dataPoints.length - 1])
        }
      })
    );
  }

  return plugins;
}
