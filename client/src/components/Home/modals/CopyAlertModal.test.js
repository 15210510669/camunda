/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';

import CopyAlertModal from './CopyAlertModal';

const props = {
  initialAlertName: 'test',
  onConfirm: jest.fn(),
};

it('should update the alert name', () => {
  const node = shallow(<CopyAlertModal {...props} />);

  expect(node.find(LabeledInput).prop('value')).toBe('test (copy)');

  node.find(LabeledInput).simulate('change', {target: {value: 'new alert'}});
  node.find('[primary]').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('new alert');
});
