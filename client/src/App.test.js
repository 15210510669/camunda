/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AppWithErrorHandling from './App';

jest.mock('notifications', () => ({addNotification: jest.fn(), Notifications: () => <span />}));

const App = AppWithErrorHandling.WrappedComponent;

jest.mock('translation', () => ({
  initTranslation: jest.fn(),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should show an error message when it is not possible to initilize the translation', async () => {
  const node = shallow(<App {...props} error="test error message" />);

  expect(node).toMatchSnapshot();
});

it('should render the last component in the url', async () => {
  const node = shallow(<App {...props} />);
  await node.update();

  const renderedEntity = shallow(
    node.find({path: '/(report|dashboard|collection|events/processes)/*'}).prop('render')({
      location: {pathname: '/collection/cid/dashboard/did/report/rid'},
    })
  );

  expect(renderedEntity.dive().name()).toBe('Report');
  expect(renderedEntity.props().match.params.id).toBe('rid');
});
