/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {ReportRenderer, Modal} from 'components';
import {evaluateReport} from 'services';

import {RawDataModal} from './RawDataModal';

const props = {
  name: 'processName',
  report: {data: {configuration: {xml: 'xml data'}}},
  close: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

jest.mock('config', () => ({newReport: {new: {data: {configuration: {}}}}}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({}),
}));

it('should contain ReportRenderer', () => {
  const node = shallow(<RawDataModal {...props} />);
  runLastEffect();
  expect(node.find(Modal)).toExist();
  expect(node.find(ReportRenderer)).toExist();
});

it('evaluate the raw data of the report on mount', () => {
  shallow(<RawDataModal {...props} />);
  runLastEffect();
  expect(evaluateReport).toHaveBeenCalledWith(
    {
      data: {
        configuration: {
          xml: 'xml data',
        },
        groupBy: {type: 'none', value: null},
        view: {entity: null, properties: ['rawData']},
        visualization: 'table',
      },
    },
    [],
    undefined
  );
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  const testError = {message: 'testError', reportDefinition: {}, status: 400};
  const mightFail = (promise, cb, err) => err(testError);

  const node = shallow(<RawDataModal {...props} mightFail={mightFail} />);
  runLastEffect();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual({status: 400, ...testError});
});
