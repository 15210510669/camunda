/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {render, screen, waitFor} from '@testing-library/react';
import {mockDecisionInstance} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Drd} from '.';

describe('<Drd />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstance))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    decisionInstanceStore.init('337423841237089');
  });

  afterEach(() => {
    decisionInstanceStore.reset();
  });

  it('should render DRD', async () => {
    render(<Drd />, {wrapper: ThemeProvider});

    await waitFor(() =>
      expect(screen.getByText('Default View mock')).toBeInTheDocument()
    );
    expect(screen.getByText('Definitions Name Mock')).toBeInTheDocument();
  });
});
