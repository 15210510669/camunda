/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter, Route} from 'react-router-dom';
import {
  render,
  screen,
  fireEvent,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {variables} from 'modules/stores/variables';
import {currentInstance} from 'modules/stores/currentInstance';
import Variables from './index';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {mockVariables} from './index.setup';
import PropTypes from 'prop-types';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const EMPTY_PLACEHOLDER = 'The Flow Node has no variables.';

const Wrapper = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/instances/1`]}>
        <Route path="/instances/:id">{children} </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('Variables', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json(null))
      ),
      rest.get('/api/workflow-instances/:instanceId', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 1,
            state: 'ACTIVE',
          })
        )
      ),
      rest.get(
        '/api/workflow-instances/:instanceId/variables?scopeId=:scopeId',
        (_, res, ctx) => res.once(ctx.json(mockVariables))
      )
    );

    await currentInstance.init(1);
  });

  afterEach(() => {
    currentInstance.reset();
    variables.reset();
  });

  describe('Skeleton', () => {
    it('should display empty content if there are no variables', async () => {
      mockServer.use(
        rest.get(
          '/api/workflow-instances/:instanceId/variables?scopeId=:scopeId',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      render(<Variables />, {wrapper: Wrapper});

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
    });

    it('should display skeleton on initial load', async () => {
      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    });

    it('should display spinner on second variable fetch', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      mockServer.use(
        rest.get(
          '/api/workflow-instances/:instanceId/variables?scopeId=:scopeId',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      const variableList = variables.fetchVariables(1);

      expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
      await variableList;
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
    });
  });

  describe('Variables', () => {
    it('should render variables table', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Variable')).toBeInTheDocument();
      expect(screen.getByText('Value')).toBeInTheDocument();

      const {items} = variables.state;

      items.forEach((item) => {
        const withinVariableRow = within(screen.getByTestId(item.name));
        expect(withinVariableRow.getByText(item.name)).toBeInTheDocument();
        expect(withinVariableRow.getByText(item.value)).toBeInTheDocument();
      });
    });

    it('should show/hide spinner next to variable according to it having an active operation', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const {items} = variables.state;
      const [activeOperationVariable] = items.filter(
        ({hasActiveOperation}) => hasActiveOperation
      );

      expect(
        within(screen.getByTestId(activeOperationVariable.name)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      const [inactiveOperationVariable] = items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(
          screen.queryByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-spinner')
      ).not.toBeInTheDocument();
    });
  });

  describe('Add variable', () => {
    it('should show/hide add variable inputs', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.getByTestId('add-key-row')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
    });

    it('should validate when adding variable', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /variable/i}), {
        target: {value: 'test'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: 'test'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '"test"'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '123'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '{}'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();

      fireEvent.change(screen.getByRole('textbox', {name: /variable/i}), {
        target: {value: '"test"'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();

      fireEvent.change(screen.getByRole('textbox', {name: /variable/i}), {
        target: {value: 'test'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();

      const invalidJSONObject = "{invalidKey: 'value'}";

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: invalidJSONObject},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();

      // already existing variable
      fireEvent.change(screen.getByRole('textbox', {name: /variable/i}), {
        target: {value: variables.state.items[0].name},
      });

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: variables.state.items[0].value},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
    });

    it('should save new variable', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      const newVariableName = 'newVariable';
      const newVariableValue = '1234';

      fireEvent.change(screen.getByRole('textbox', {name: /variable/i}), {
        target: {value: newVariableName},
      });
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: newVariableValue},
      });

      fireEvent.click(screen.getByRole('button', {name: 'Save variable'}));

      expect(
        within(screen.getByTestId(newVariableName)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      mockServer.use(
        rest.get(
          '/api/workflow-instances/:instanceId/variables?scopeId=:scopeId',
          (_, res, ctx) =>
            res.once(
              ctx.json([
                ...mockVariables,
                {
                  id: '2251799813686037-mwst',
                  name: 'newVariable',
                  value: '1234',
                  scopeId: '2251799813686037',
                  workflowInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                },
              ])
            )
        )
      );

      await variables.fetchVariables('with-newly-added-variable');
      expect(
        within(screen.queryByTestId(newVariableName)).queryByTestId(
          'edit-variable-spinner'
        )
      ).not.toBeInTheDocument();
    });
  });

  describe('Edit variable', () => {
    it('should show/hide edit button next to variable according to it having an active operation', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const [activeOperationVariable] = variables.state.items.filter(
        ({hasActiveOperation}) => hasActiveOperation
      );

      expect(
        within(
          screen.queryByTestId(activeOperationVariable.name)
        ).queryByTestId('edit-variable-button')
      ).not.toBeInTheDocument();

      const [inactiveOperationVariable] = variables.state.items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(screen.getByTestId(inactiveOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();
    });

    it('should not display edit button next to variables if instance is completed or canceled', async () => {
      jest.useFakeTimers();

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const [inactiveOperationVariable] = variables.state.items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(screen.getByTestId(inactiveOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();

      mockServer.use(
        rest.get('/api/workflow-instances/:instanceId', (_, res, ctx) =>
          res.once(
            ctx.json({
              id: 2,
              state: 'CANCELED',
            })
          )
        )
      );

      jest.advanceTimersByTime(5000);
      await waitForElementToBeRemoved(
        within(
          screen.getByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-button'),
        {timeout: 5000}
      );
      jest.useRealTimers();
    });

    it('should show/hide edit variable inputs', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );
      expect(
        withinFirstVariable.queryByTestId('edit-value')
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByRole('button', {name: 'Exit edit mode'})
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByRole('button', {name: 'Save variable'})
      ).not.toBeInTheDocument();

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(withinFirstVariable.getByTestId('edit-value')).toBeInTheDocument();
      expect(
        withinFirstVariable.getByRole('button', {name: 'Exit edit mode'})
      ).toBeInTheDocument();
      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeInTheDocument();
    });

    it('should disable save button when nothing is changed', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
    });

    it('should validate when editing variables', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      const emptyValue = '';

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: emptyValue},
      });

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();

      const invalidJSONObject = "{invalidKey: 'value'}";

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: invalidJSONObject},
      });

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
    });
  });

  describe('Footer', () => {
    beforeAll(async () => {
      flowNodeInstance.setCurrentSelection({flowNodeId: null, treeRowIds: []});
    });
    it('should disable add variable button when loading', async () => {
      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByText('Add Variable')).toBeDisabled();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button if instance state is cancelled', async () => {
      currentInstance.setCurrentInstance({id: 1, state: 'CANCELED'});

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeDisabled();
    });

    it('should disable add variable button if add/edit variable button is clicked', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();

      fireEvent.click(screen.getAllByTestId('edit-variable-button')[0]);
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button when clicked', async () => {
      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeEnabled();
      fireEvent.click(screen.getByText('Add Variable'));
      expect(screen.getByText('Add Variable')).toBeDisabled();
    });
  });
});
