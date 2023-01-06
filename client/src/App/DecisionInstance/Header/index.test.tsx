/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Header} from './index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

const MOCK_DECISION_INSTANCE_ID = '123567';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <ThemeProvider>
    <MemoryRouter initialEntries={[`/decisions/${MOCK_DECISION_INSTANCE_ID}`]}>
      <Routes>
        <Route path="/decisions/:decisionInstanceId" element={children} />
      </Routes>
    </MemoryRouter>
  </ThemeProvider>
);

describe('<Header />', () => {
  it('should show a loading skeleton', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID
    );

    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByTestId('details-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton')
    );
  });

  it('should show the decision instance details', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID
    );

    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByTestId('evaluated-icon')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open decision requirements diagram/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^decision name$/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /decision instance key/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /version/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /evaluation date/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /process instance key/i})
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {
        name: invoiceClassification.decisionName,
      })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {name: MOCK_DECISION_INSTANCE_ID})
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('link', {
        description: `View decision ${invoiceClassification.decisionName} version ${invoiceClassification.decisionVersion} instances`,
      })
    ).toHaveTextContent(invoiceClassification.decisionVersion.toString());
    expect(
      await screen.findByRole('cell', {
        name: '2018-12-12 00:00:00',
      })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('link', {
        description: `View process instance ${invoiceClassification.processInstanceId}`,
      })
    ).toBeInTheDocument();
  });
});
