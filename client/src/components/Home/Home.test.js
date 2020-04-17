/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Home} from './Home';
import {loadEntities} from './service';

jest.mock('./service', () => ({
  loadEntities: jest.fn().mockReturnValue([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      data: {
        roleCounts: {},
        subEntityCounts: {},
      },
      reportType: 'process',
      combined: false,
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load entities', () => {
  shallow(<Home {...props} />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should display the user name', () => {
  const node = shallow(<Home {...props} user={{name: 'John Doe'}} />);

  expect(node.find('.welcomeMessage')).toIncludeText('John Doe');
});
