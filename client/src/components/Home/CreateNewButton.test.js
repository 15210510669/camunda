/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';

import CreateNewButton from './CreateNewButton';

it('should match snapshot', () => {
  const node = shallow(<CreateNewButton />);

  expect(node).toMatchSnapshot();
});

it('should not show the collection option if it is in a collection', () => {
  const node = shallow(<CreateNewButton collection="123" />);

  expect(node).toMatchSnapshot();
});

it('should call the createCollection prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton createCollection={spy} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});
