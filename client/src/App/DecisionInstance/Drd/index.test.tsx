/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {render, screen, waitFor} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {drdDataStore} from 'modules/stores/drdData';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Drd} from '.';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('<Drd />', () => {
  beforeEach(() => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDrdData().withSuccess(mockDrdData);

    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    drdDataStore.init();
    decisionXmlStore.init();
    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');
  });

  afterEach(() => {
    decisionInstanceDetailsStore.reset();
    decisionXmlStore.reset();
    drdDataStore.reset();
  });

  it('should render DRD', async () => {
    render(<Drd />, {wrapper: Wrapper});

    await waitFor(() =>
      expect(screen.getByText('Default View mock')).toBeInTheDocument()
    );
    expect(screen.getByText('Definitions Name Mock')).toBeInTheDocument();
  });
});
