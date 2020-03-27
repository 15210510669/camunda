/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import LoadingIndicator from './LoadingIndicator';

it('should render without crashing', () => {
  shallow(<LoadingIndicator />);
});

it('should create a label with the provided id', () => {
  const node = shallow(<LoadingIndicator id="someId" />);

  expect(node).toHaveProp('id', 'someId');
});

it('should be possible to get a smaller version', () => {
  const node = shallow(<LoadingIndicator small />);

  expect(node).toHaveClassName('small');
});
