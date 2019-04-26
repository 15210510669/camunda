/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import DashboardReport from './DashboardReport';

jest.mock('./ExternalReport', () => ({children}) => <span>ExternalReport: {children()}</span>);
jest.mock('./OptimizeReport', () => ({children}) => <span>OptimizeReport: {children()}</span>);

const props = {
  report: {
    id: 'a'
  }
};

it('should render optional addons', () => {
  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = mount(
    <DashboardReport
      {...props}
      addons={[<TextRenderer key="textAddon">I am an addon!</TextRenderer>]}
    />
  );

  expect(node).toIncludeText('I am an addon!');
});

it('should pass properties to report addons', () => {
  const PropsRenderer = props => <p>{JSON.stringify(Object.keys(props))}</p>;

  const node = mount(
    <DashboardReport {...props} addons={[<PropsRenderer key="propsRenderer" />]} />
  );

  expect(node).toIncludeText('report');
  expect(node).toIncludeText('tileDimensions');
});
