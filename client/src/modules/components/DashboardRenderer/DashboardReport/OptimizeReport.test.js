/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ThemedOptimizeReport from './OptimizeReport';
import {ReportRenderer} from 'components';

const {WrappedComponent: OptimizeReportWithErrorHandling} = ThemedOptimizeReport;
const {WrappedComponent: OptimizeReport} = OptimizeReportWithErrorHandling;

const loadReport = jest.fn();

const props = {
  report: {
    id: 'a'
  },
  loadReport,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  location: {
    pathname: '/dashboard/did/'
  }
};

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: fn => fn
  };
});

it('should load the report provided by id', () => {
  shallow(<OptimizeReport {...props} />);

  expect(loadReport).toHaveBeenCalledWith(props.report.id);
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadReport.mockReturnValue('data');

  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node.find(ReportRenderer)).toExist();
});

it('should contain the report name', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node.find('Link').children()).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('Link').children()).toIncludeText('Report Name');
  expect(node.find('Link')).toHaveProp('to', '/dashboard/did/report/a/');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} disableNameLink />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('a')).not.toExist();
  expect(node).toIncludeText('Report Name');
});

it('should display the name of a failing report', async () => {
  loadReport.mockReturnValue({
    json: () => ({
      reportDefinition: {name: 'Failing Name'}
    })
  });
  const node = shallow(
    <OptimizeReport {...props} mightFail={(data, success, fail) => fail(data)} disableNameLink />
  );

  await node.instance().loadReport();

  expect(node).toIncludeText('Failing Name');
});

it('should display an error message if there is an error and no report is returned', async () => {
  loadReport.mockReturnValue({
    json: () => ({
      errorMessage: 'Is failing',
      reportDefinition: null
    })
  });

  const node = shallow(
    <OptimizeReport {...props} mightFail={(data, success, fail) => fail(data)} disableNameLink />
  );

  await node.instance().loadReport();
  expect(node.find('NoDataNotice').prop('children')).toBe('Is failing');
});
