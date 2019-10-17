/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import LabeledInput from './LabeledInput';
import {Input} from 'components';

it('should create a input with the provided id', () => {
  const node = shallow(<LabeledInput id="someId" label="test" />);

  expect(node.find(Input)).toHaveProp('id', 'someId');
});

it('should include the child content', () => {
  const node = shallow(<LabeledInput>some child content</LabeledInput>);

  expect(node).toIncludeText('some child content');
});

it('should can be disabled', () => {
  const node = shallow(<LabeledInput disabled>some child content</LabeledInput>);

  expect(node.find('Labeled')).toHaveProp('disabled', true);
});
