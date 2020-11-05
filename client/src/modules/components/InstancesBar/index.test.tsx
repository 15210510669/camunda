/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, within} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import InstancesBar from './index';

describe('InstancesBar', () => {
  it('should display the right data', () => {
    render(
      <InstancesBar
        incidentsCount={10}
        label="someLabel"
        activeCount={8}
        barHeight={5}
        size="small"
      />,
      {wrapper: ThemeProvider}
    );

    expect(
      within(screen.getByTestId('incident-instances-badge')).getByText('10')
    ).toBeInTheDocument();
    expect(screen.getByText('someLabel')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('active-instances-badge')).getByText('8')
    ).toBeInTheDocument();
  });

  it('should not display active instance count if has invalid active instances count', () => {
    render(
      <InstancesBar
        incidentsCount={10}
        label="someLabel"
        activeCount={-1}
        barHeight={5}
        size="small"
      />,
      {wrapper: ThemeProvider}
    );

    expect(
      screen.queryByTestId('active-instances-badge')
    ).not.toBeInTheDocument();
  });
});
