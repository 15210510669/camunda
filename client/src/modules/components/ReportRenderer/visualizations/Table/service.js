import {get} from 'request';

import {reportConfig, formatters} from 'services';
import {getRelativeValue} from '../service';

const {
  options: {view, groupBy},
  getLabelFor
} = reportConfig.process;

const {formatReportResult} = formatters;

export async function getCamundaEndpoints() {
  const response = await get('api/camunda');
  return await response.json();
}

export function getFormattedLabels(
  reportsLabels,
  reportsNames,
  displayRelativeValue,
  displayAbsoluteValue
) {
  return reportsLabels.reduce(
    (prev, reportLabels, i) => [
      ...prev,
      {
        label: reportsNames[i],
        columns: [
          ...(displayAbsoluteValue ? reportLabels.slice(1) : []),
          ...(displayRelativeValue ? ['Relative Frequency'] : [])
        ]
      }
    ],
    []
  );
}

export function getBodyRows(
  unitedResults,
  allKeys,
  formatter,
  displayRelativeValue,
  processInstanceCount,
  displayAbsoluteValue
) {
  const rows = allKeys.map(key => {
    const row = [key];
    unitedResults.forEach((result, i) => {
      const value = result[key];
      if (displayAbsoluteValue) row.push(formatter(typeof value !== 'undefined' ? value : ''));
      if (displayRelativeValue) row.push(getRelativeValue(value, processInstanceCount[i]));
    });
    return row;
  });
  return rows;
}

export function getCombinedTableProps(reportResult, reports) {
  const initialData = {
    labels: [],
    reportsNames: [],
    combinedResult: [],
    processInstanceCount: []
  };

  const combinedProps = reports.reduce((prevReport, {id}) => {
    const report = reportResult[id];
    const {data, result, processInstanceCount, name} = report;
    const {
      configuration: {xml}
    } = data;

    // build 2d array of all labels
    const viewLabel = getLabelFor(view, data.view, xml);
    const groupByLabel = getLabelFor(groupBy, data.groupBy, xml);
    const labels = [...prevReport.labels, [groupByLabel, viewLabel]];

    // 2d array of all names
    const reportsNames = [...prevReport.reportsNames, name];

    // 2d array of all results
    const formattedResult = formatReportResult(data, result);
    const reportsResult = [...prevReport.combinedResult, formattedResult];

    // 2d array of all process instances count
    const reportsProcessInstanceCount = [...prevReport.processInstanceCount, processInstanceCount];

    return {
      labels,
      reportsNames,
      combinedResult: reportsResult,
      processInstanceCount: reportsProcessInstanceCount
    };
  }, initialData);

  return combinedProps;
}
