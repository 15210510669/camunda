import React from 'react';
import ReportBlankSlate from './ReportBlankSlate';

import {Table, Number, Chart, DecisionTable} from './visualizations';

import {getFormatter} from './service';

const getComponent = (groupBy, visualization) => {
  switch (visualization) {
    case 'number':
      return Number;
    case 'table':
      return groupBy === 'matchedRule' ? DecisionTable : Table;
    case 'bar':
    case 'line':
    case 'pie':
      return Chart;
    default:
      return ReportBlankSlate;
  }
};

export default function DecisionReportRenderer(props) {
  const {
    visualization,
    view,
    groupBy: {type}
  } = props.report.data;
  const Component = getComponent(type, visualization);

  return (
    <div className="component">
      <Component {...props} formatter={getFormatter(view.property)} />
    </div>
  );
}
