/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import NumberConfig from './NumberConfig';

const props = {
  report: {
    data: {
      view: {property: 'frequency'},
      configuration: {
        precision: null,
        targetValue: {
          active: false,
          countProgress: {baseline: '0', target: '100'},
          durationProgress: {
            baseline: {
              value: '0',
              unit: 'hours'
            },
            target: {
              value: '2',
              unit: 'hours'
            }
          }
        }
      }
    }
  }
};

it('should have a switch for the precision setting', () => {
  const spy = jest.fn();
  const node = shallow(<NumberConfig {...props} onChange={spy} />);

  expect(node.find('Switch')).toExist();
  expect(node.find('.precision')).toExist();

  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 1}});
});

it('should change the precision', () => {
  props.report.data.configuration.precision = 5;

  const spy = jest.fn();
  const node = shallow(<NumberConfig {...props} onChange={spy} />);

  node.find('.precision').simulate('keydown', {key: '3'});

  expect(spy).toHaveBeenCalledWith({precision: {$set: 3}});
});

it('should contain a target input for count property', () => {
  const node = shallow(<NumberConfig {...props} />);

  expect(node.find('CountTargetInput')).toExist();
});

it('should contain a target input for duration property', () => {
  props.report.data.view.property = 'duration';
  const node = shallow(<NumberConfig {...props} />);

  expect(node.find('CountTargetInput')).not.toExist();
  expect(node.find('DurationTargetInput')).toExist();
});
