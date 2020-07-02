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

jest.mock('react', () => {
  const outstandingEffects = [];
  return {
    ...jest.requireActual('react'),
    useEffect: (fn) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()();
      }
    },
  };
});

it('should contain ReportRenderer', () => {
  const node = shallow(<RawDataModal {...props} />);
  runLastEffect();
  expect(node.find(Modal)).toExist();
  expect(node.find(ReportRenderer)).toExist();
});

it('evaluate the raw data of the report on mount', () => {
  shallow(<RawDataModal {...props} />);
  runLastEffect();
  expect(evaluateReport).toHaveBeenCalledWith({
    data: {
      configuration: {
        xml: 'xml data',
      },
      groupBy: {type: 'none', value: null},
      view: {entity: null, property: 'rawData'},
      visualization: 'table',
    },
  });
});
