/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {IS_DATE_RANGE_FILTERS_ENABLED} from 'modules/feature-flags';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('Optional Filters', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    await processDiagramStore.fetchProcessDiagram('bigVarProcess');
    jest.useFakeTimers();
  });

  afterEach(() => {
    processesStore.reset();
    processDiagramStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should initially hide optional filters', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(
      screen.queryByTestId('optional-filter-variable-name')
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance key\(s\)/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/operation id/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/Parent Process Instance Key/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/error message/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();
  });

  it('should display variable fields on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));

    expect(
      screen.getByTestId('optional-filter-variable-name')
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
    await user.click(screen.getByText(/^more filters$/i));
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(
        'Variable'
      )
    ).not.toBeInTheDocument();
  });

  it('should display instance ids field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.click(screen.getByText(/^more filters$/i));

    expect(
      screen.getByLabelText(/process instance key\(s\)/i)
    ).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(
        'Process Instance Key(s)'
      )
    ).not.toBeInTheDocument();
  });

  it('should display operation id field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.click(screen.getByText(/^more filters$/i));

    expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(
        'Operation Id'
      )
    ).not.toBeInTheDocument();
  });

  it('should display Parent Process Instance Key field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.click(screen.getByText(/^more filters$/i));

    expect(
      screen.getByLabelText(/Parent Process Instance Key/i)
    ).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(
        'Parent Process Instance Key'
      )
    ).not.toBeInTheDocument();
  });

  it('should display error message field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Error Message'));
    await user.click(screen.getByText(/^more filters$/i));

    expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(
        'Error Message'
      )
    ).not.toBeInTheDocument();
  });

  it('should display start date field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    const LABEL = IS_DATE_RANGE_FILTERS_ENABLED
      ? 'Start Date Range'
      : 'Start Date';

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(LABEL));
    await user.click(screen.getByText(/^more filters$/i));

    expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(LABEL)
    ).not.toBeInTheDocument();
  });

  it('should display end date field on click', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    const LABEL = IS_DATE_RANGE_FILTERS_ENABLED ? 'End Date Range' : 'End Date';

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText(LABEL));
    await user.click(screen.getByText(/^more filters$/i));

    expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      within(screen.getByTestId('more-filters-dropdown')).queryByText(LABEL)
    ).not.toBeInTheDocument();
  });

  it('should hide more filters button when all optional filters are visible', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Error Message'));

    if (IS_DATE_RANGE_FILTERS_ENABLED) {
      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date Range'));
      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date Range'));
    } else {
      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));
    }

    expect(
      screen.queryByTestId('more-filters-dropdown')
    ).not.toBeInTheDocument();

    await user.click(screen.getByTestId('delete-variable'));

    expect(screen.getByText(/^more filters$/i)).toBeInTheDocument();
  });

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'should delete optional filters',
    async () => {
      const MOCK_PARAMS = {
        process: 'bigVarProcess',
        version: '1',
        ids: '2251799813685467',
        parentInstanceId: '1954699813693756',
        errorMessage: 'a random error',
        startDate: '2021-02-21 18:17:18',
        endDate: '2021-02-23 18:17:18',
        flowNodeId: 'ServiceTask_0kt6c5i',
        variableName: 'foo',
        variableValue: '"bar"',
        operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      } as const;

      const {user} = render(<Filters />, {
        wrapper: getWrapper(
          `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        ),
      });

      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
      );

      expect(
        screen.getByLabelText(/process instance key\(s\)/i)
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText(/Parent Process Instance Key/i)
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();

      expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();

      expect(
        screen.getByTestId('optional-filter-variable-name')
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();

      await user.click(screen.getByTestId('delete-ids'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              parentInstanceId: '1954699813693756',
              errorMessage: 'a random error',
              startDate: '2021-02-21 18:17:18',
              endDate: '2021-02-23 18:17:18',
              flowNodeId: 'ServiceTask_0kt6c5i',
              variableName: 'foo',
              variableValue: '"bar"',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );

      expect(
        screen.queryByLabelText(/process instance key\(s\)/i)
      ).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-parentInstanceId'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              errorMessage: 'a random error',
              startDate: '2021-02-21 18:17:18',
              endDate: '2021-02-23 18:17:18',
              flowNodeId: 'ServiceTask_0kt6c5i',
              variableName: 'foo',
              variableValue: '"bar"',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(
        screen.queryByLabelText(/Parent Process Instance Key/i)
      ).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-errorMessage'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              startDate: '2021-02-21 18:17:18',
              endDate: '2021-02-23 18:17:18',
              flowNodeId: 'ServiceTask_0kt6c5i',
              variableName: 'foo',
              variableValue: '"bar"',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(screen.queryByLabelText(/error message/i)).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-startDate'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              endDate: '2021-02-23 18:17:18',
              flowNodeId: 'ServiceTask_0kt6c5i',
              variableName: 'foo',
              variableValue: '"bar"',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-endDate'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              flowNodeId: 'ServiceTask_0kt6c5i',
              variableName: 'foo',
              variableValue: '"bar"',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-variable'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              flowNodeId: 'ServiceTask_0kt6c5i',
              operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(
        screen.queryByTestId('optional-filter-variable-name')
      ).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();

      await user.click(screen.getByTestId('delete-operationId'));

      await waitFor(() =>
        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(
            Object.entries({
              process: 'bigVarProcess',
              version: '1',
              flowNodeId: 'ServiceTask_0kt6c5i',
              active: 'true',
              incidents: 'true',
              completed: 'true',
              canceled: 'true',
            })
          ).toString()}`
        )
      );
      expect(screen.queryByLabelText(/operation id/i)).not.toBeInTheDocument();
    }
  );

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'should remove optional filters on filter reset',
    async () => {
      const MOCK_PARAMS = {
        process: 'bigVarProcess',
        version: '1',
        ids: '2251799813685467',
        parentInstanceId: '1954699813693756',
        errorMessage: 'a random error',
        startDate: '2021-02-21 18:17:18',
        endDate: '2021-02-23 18:17:18',
        flowNodeId: 'ServiceTask_0kt6c5i',
        variableName: 'foo',
        variableValue: '"bar"',
        operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      } as const;

      const {user} = render(<Filters />, {
        wrapper: getWrapper(
          `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        ),
      });

      expect(screen.getByTestId('search').textContent).toBe(
        `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
      );

      expect(
        screen.getByLabelText(/process instance key\(s\)/i)
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText(/Parent Process Instance Key/i)
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
      expect(
        screen.getByTestId('optional-filter-variable-name')
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();

      await user.click(screen.getByTitle(/reset filters/i));

      await waitFor(() =>
        expect(screen.getByTestId('search')).toHaveTextContent(
          /^\?active=true&incidents=true$/
        )
      );

      expect(
        screen.queryByLabelText(/process instance key\(s\)/i)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByLabelText(/Parent Process Instance Key/i)
      ).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/error message/i)).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('optional-filter-variable-name')
      ).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/operation id/i)).not.toBeInTheDocument();
    }
  );
});
