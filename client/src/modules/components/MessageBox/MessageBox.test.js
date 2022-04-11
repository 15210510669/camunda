/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import MessageBox from './MessageBox';

it('renders without crashing', () => {
  shallow(<MessageBox />);
});

it('renders the message text provided as a property', () => {
  const text = 'This is a Message!';

  const node = shallow(<MessageBox>{text}</MessageBox>);
  expect(node).toIncludeText(text);
});

it('renders the class name as provided as a property', () => {
  const type = 'test';

  const node = shallow(<MessageBox type={type} />);
  expect(node.find('.MessageBox')).toHaveClassName('MessageBox--test');
});
