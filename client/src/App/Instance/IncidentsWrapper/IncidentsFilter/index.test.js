/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsFilter} from './index';
import {render, screen, fireEvent} from '@testing-library/react';

import {
  defaultProps,
  selectedErrorPillProps,
  mockIncidents,
  mockIncidentsWithManyErrors,
} from './index.setup';
import {incidents as incidentsStore} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('IncidentsFilter', () => {
  afterAll(() => {
    incidentsStore.reset();
  });
  it('should render pills by incident type', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );

    await incidentsStore.fetchIncidents(1);

    render(<IncidentsFilter {...defaultProps} />, {wrapper: ThemeProvider});

    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Condition error 2'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Extract value error 1'})
    ).toBeInTheDocument();
  });

  it('should render pills by flow node', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );
    await incidentsStore.fetchIncidents(1);

    render(<IncidentsFilter {...defaultProps} />, {wrapper: ThemeProvider});
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'flowNodeId_exclusiveGateway 1'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'flowNodeId_alwaysFailingTask 2'})
    ).toBeInTheDocument();
  });

  it('should show a more button', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsWithManyErrors))
      )
    );
    await incidentsStore.fetchIncidents(1);

    render(<IncidentsFilter {...defaultProps} />, {wrapper: ThemeProvider});
    expect(
      screen.queryByRole('button', {name: 'error type 6 1'})
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: /^1 more/}));

    expect(
      screen.getByRole('button', {name: 'error type 6 1'})
    ).toBeInTheDocument();
  });

  it('should disable/enable clear all button depending on selected pills', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );
    await incidentsStore.fetchIncidents(1);

    const {rerender} = render(<IncidentsFilter {...defaultProps} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByRole('button', {name: 'Clear All'})).toBeDisabled();
    fireEvent.click(screen.getByRole('button', {name: 'Clear All'}));
    expect(defaultProps.onClearAll).not.toHaveBeenCalled();

    rerender(<IncidentsFilter {...selectedErrorPillProps} />);
    expect(screen.getByRole('button', {name: 'Clear All'})).toBeEnabled();
    fireEvent.click(screen.getByRole('button', {name: 'Clear All'}));
    expect(defaultProps.onClearAll).toHaveBeenCalled();
  });
});
