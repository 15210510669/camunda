/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import Checkmark from './Checkmark';

it('should match snapshot', () => {
  const node = shallow(<Checkmark event={{eventLabel: 'event started', source: 'camunda'}} />);

  expect(node).toMatchSnapshot();
});
