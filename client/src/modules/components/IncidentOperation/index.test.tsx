/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createIncident} from 'modules/testUtils';
import {IncidentOperation} from './index';
import {render, screen, fireEvent} from '@testing-library/react';
import {operationsStore} from 'modules/stores/operations';
import {mockOperationCreated, mockProps} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('IncidentOperation', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should not render a spinner', () => {
    render(<IncidentOperation {...mockProps} />, {wrapper: ThemeProvider});
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
  });

  it('should render a spinner if it is forced', () => {
    render(<IncidentOperation {...mockProps} showSpinner={true} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should render a spinner when instance operation is applied', async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json(mockOperationCreated))
      )
    );

    render(
      <IncidentOperation
        incident={createIncident()}
        instanceId={'instance_1'}
      />,
      {
        wrapper: ThemeProvider,
      }
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Retry Incident'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should render a spinner when retry button is clicked', () => {
    render(
      <IncidentOperation
        incident={createIncident()}
        instanceId={'instance_1'}
      />,
      {wrapper: ThemeProvider}
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('retry-incident'));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });
});
