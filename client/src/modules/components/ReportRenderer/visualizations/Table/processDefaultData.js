import {reportConfig, formatters, isDurationReport} from 'services';
import {getRelativeValue} from '../service';

const {formatReportResult} = formatters;

export default function processDefaultData({formatter = v => v, report, flowNodeNames = {}}) {
  const {data, result, reportType, processInstanceCount, decisionInstanceCount} = report;
  const {
    configuration: {hideAbsoluteValue, hideRelativeValue, xml},
    view,
    groupBy
  } = data;

  const formattedResult = formatReportResult(data, result);
  const instanceCount = processInstanceCount || decisionInstanceCount || 0;
  const config = reportConfig[reportType];
  const labels = [
    config.getLabelFor(config.options.groupBy, groupBy, xml),
    config.getLabelFor(config.options.view, view, xml)
  ];

  if (view.entity === 'userTask') {
    labels[0] = 'User Task';
  }

  const displayRelativeValue = view.property === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = isDurationReport(report) || !hideAbsoluteValue;

  if (!displayAbsoluteValue) {
    labels.length = 1;
  }

  // normal two-dimensional data
  return {
    head: [...labels, ...(displayRelativeValue ? ['Relative Frequency'] : [])],
    body: Object.keys(formattedResult).map(key => [
      flowNodeNames[key] || key,
      ...(displayAbsoluteValue ? [formatter(formattedResult[key])] : []),
      ...(displayRelativeValue ? [getRelativeValue(formattedResult[key], instanceCount)] : [])
    ])
  };
}
