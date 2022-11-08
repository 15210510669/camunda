/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {render, screen} from 'modules/testing-library';
import {
  invoiceClassification,
  assignApproverGroup,
  literalExpression,
} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionPanel} from '.';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';

describe('<DecisionPanel />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    mockFetchDecisionXML().withSuccess(mockDmnXml);
    decisionXmlStore.init();
  });

  afterEach(() => {
    decisionXmlStore.reset();
    decisionInstanceDetailsStore.reset();
  });

  it('should render decision table', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText('DecisionTable view mock')
    ).toBeInTheDocument();
    expect(screen.queryByText('An error occurred')).not.toBeInTheDocument();
  });

  it('should render literal expression', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(literalExpression))
      )
    );

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText('LiteralExpression view mock')
    ).toBeInTheDocument();
  });

  it('should render incident banner', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(assignApproverGroup))
      )
    );

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: ThemeProvider});

    expect(await screen.findByText('An error occurred')).toBeInTheDocument();
  });
});
