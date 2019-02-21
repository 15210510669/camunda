import {formatters} from 'services';

const {formatReportResult} = formatters;

// Override the default generate legend's labels function
// This is done to modify the colors retrieval method of the legend squares and filter unneeded labels
export function generateLegendLabels(chart) {
  const data = chart.data;
  return data.datasets.length
    ? data.datasets
        .map(function(dataset) {
          return {
            text: dataset.label,
            fillStyle: !dataset.backgroundColor.length
              ? dataset.backgroundColor
              : dataset.legendColor,
            strokeStyle: dataset.legendColor
          };
        }, this)
        .filter(dataset => {
          return dataset.text;
        })
    : [];
}

export function getCombinedChartProps(result, data) {
  return data.reports.reduce(
    (prev, {id, color}) => {
      const report = result[id];
      let singleReportResult;
      if (data.visualization === 'number') {
        singleReportResult = {[report.name]: report.result};
      } else {
        singleReportResult = formatReportResult(data, report.result);
      }

      return {
        resultArr: [...prev.resultArr, singleReportResult],
        reportsNames: [...prev.reportsNames, report.name],
        reportColors: [...prev.reportColors, color]
      };
    },
    {resultArr: [], reportsNames: [], reportColors: []}
  );
}
