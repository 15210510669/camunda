/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {getFlowNodeNames} from 'services';

import CombinedReportRendererWithErrorHandling from './CombinedReportRenderer';
import {Chart, Table} from './visualizations';

import {processResult} from './service';

const CombinedReportRenderer = CombinedReportRendererWithErrorHandling.WrappedComponent;

jest.mock('./service', () => {
  return {
    isEmpty: str => !str,
    getFormatter: view => v => v,
    processResult: jest.fn().mockImplementation(({result}) => result)
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    formatters: {formatReportResult: (data, result) => result},
    getFlowNodeNames: jest.fn().mockReturnValue({a: 'Flownode A', b: 'Flownode B'})
  };
});

const reportA = {
  name: 'report A',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      property: 'foo'
    },
    groupBy: {
      type: 'processInstance',
      unit: 'day'
    },
    visualization: 'table',
    configuration: {}
  },

  result: {
    processInstanceCount: 100,
    data: {
      '2015-03-25T12:00:00Z': 2
    }
  }
};

const CombinedReport = {
  combined: true,
  reportType: 'process',
  data: {
    configuration: {},
    reports: ['report A'],
    visualization: 'table'
  },
  result: {
    data: {
      'report A': reportA
    }
  }
};

const mightFail = jest.fn().mockImplementation((data, cb) => cb(data));

it('should provide an errorMessage property to the component', () => {
  const node = shallow(
    <CombinedReportRenderer mightFail={mightFail} report={CombinedReport} errorMessage="test" />
  );

  expect(node.find(Table)).toHaveProp('errorMessage');
});

it('should render a chart if visualization is number', () => {
  const node = shallow(
    <CombinedReportRenderer
      mightFail={mightFail}
      report={{
        ...CombinedReport,
        result: {
          data: {
            'report A': {
              ...reportA,
              data: {
                ...reportA.data,
                visualization: 'number'
              }
            }
          }
        }
      }}
    />
  );

  expect(node.find(Chart)).toBePresent();
});

it('should instruct to select one or more reports if no reports are selected for combined reports', () => {
  const report = {
    combined: true,
    data: {
      configuration: {},
      reports: []
    }
  };

  const node = shallow(<CombinedReportRenderer mightFail={mightFail} report={report} />);

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toContain('one or more reports');
});

it('should pass the report to the visualization component', () => {
  const node = shallow(<CombinedReportRenderer mightFail={mightFail} report={CombinedReport} />);

  expect(node.find(Table)).toHaveProp('report', CombinedReport);
});

it('should process the result of every report it combined', () => {
  processResult.mockClear();

  const reportB = {...reportA};
  const reportC = {...reportA};

  const report = {...CombinedReport};
  report.result = {
    data: {a: reportA, b: reportB, c: reportC}
  };

  shallow(<CombinedReportRenderer mightFail={jest.fn()} report={report} />);

  expect(processResult).toHaveBeenCalledTimes(3);
  expect(processResult).toHaveBeenCalledWith(reportA);
  expect(processResult).toHaveBeenCalledWith(reportB);
  expect(processResult).toHaveBeenCalledWith(reportC);
});

it('should load the flow node names of all processes involved', () => {
  getFlowNodeNames.mockClear();

  const reportB = {...reportA};
  reportB.data.processDefinitionKey = 'keyForB';

  const report = {...CombinedReport};
  report.result = {
    data: {a: reportA, b: reportB}
  };

  shallow(<CombinedReportRenderer mightFail={mightFail} report={report} />);

  expect(getFlowNodeNames).toHaveBeenCalledTimes(2);
  expect(getFlowNodeNames).toHaveBeenCalledWith(
    reportA.data.processDefinitionKey,
    reportA.data.processDefinitionVersion
  );
  expect(getFlowNodeNames).toHaveBeenCalledWith(
    reportB.data.processDefinitionKey,
    reportB.data.processDefinitionVersion
  );
});
