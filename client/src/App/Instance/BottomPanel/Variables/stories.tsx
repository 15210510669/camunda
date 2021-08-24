/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter, Route} from 'react-router-dom';
import {Story} from '@storybook/react';

import Variables from './index';
import {rest} from 'msw';
import {useEffect} from 'react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {mockActiveInstance} from 'modules/mocks/mockActiveInstance';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import styled from 'styled-components';
import {Form, useForm} from 'react-final-form';
import * as Styled from '../VariablePanel/styled';
import {mockBigVariables} from 'modules/mocks/mockBigVariables';
import {mockVariablesWithActiveOperation} from 'modules/mocks/mockVariablesWithActiveOperation';

export default {
  title: 'Components/Variables',
};

const Wrapper = styled.div`
  display: flex;
  height: 100%;
`;

const AddVariableWrapper = () => {
  const form = useForm();
  const {scopeId} = variablesStore;
  useEffect(() => {
    form.reset({name: '', value: ''});
  }, [form, scopeId]);

  return <Variables />;
};

const PendingVariableWrapper = () => {
  const form = useForm();
  const {scopeId} = variablesStore;
  useEffect(() => {
    form.reset({name: 'test3', value: '3'});
    variablesStore.setPendingItem({
      name: 'test3',
      value: '3',
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
      isPreview: false,
    });
  }, [form, scopeId]);

  return <Variables />;
};

const AddMode: Story = () => {
  useEffect(() => {
    flowNodeSelectionStore.init();
    currentInstanceStore.fetchCurrentInstance(1);
    variablesStore.init('1');
    variablesStore.shouldCancelOngoingRequests = false;

    return () => {
      currentInstanceStore.reset();
      flowNodeSelectionStore.reset();
      variablesStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={['/instances/6755399441055885']}>
      <Route path="/instances/:processInstanceId">
        <Wrapper>
          <Styled.VariablesPanel>
            <Form onSubmit={() => {}}>
              {({handleSubmit}) => {
                return (
                  <form onSubmit={handleSubmit}>
                    <AddVariableWrapper />
                  </form>
                );
              }}
            </Form>
          </Styled.VariablesPanel>
        </Wrapper>
      </Route>
    </MemoryRouter>
  );
};

AddMode.parameters = {
  msw: [
    rest.get('/api/process-instances/:processInstanceId', (_, res, ctx) => {
      return res(ctx.json(mockActiveInstance));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/variables',
      (_, res, ctx) => {
        return res(ctx.json(mockBigVariables));
      }
    ),
  ],
};

const EditVariableWrapper = () => {
  const form = useForm();
  const {scopeId} = variablesStore;
  useEffect(() => {
    form.reset({name: 'ab', value: '"value_ab"'});
  }, [form, scopeId]);

  return <Variables />;
};

const EditMode: Story = () => {
  useEffect(() => {
    flowNodeSelectionStore.init();
    currentInstanceStore.fetchCurrentInstance(1);
    variablesStore.init('1');
    variablesStore.shouldCancelOngoingRequests = false;

    return () => {
      currentInstanceStore.reset();
      flowNodeSelectionStore.reset();
      variablesStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={['/instances/6755399441055885']}>
      <Route path="/instances/:processInstanceId">
        <Wrapper>
          <Styled.VariablesPanel>
            <Form onSubmit={() => {}}>
              {({handleSubmit}) => {
                return (
                  <form onSubmit={handleSubmit}>
                    <EditVariableWrapper />
                  </form>
                );
              }}
            </Form>
          </Styled.VariablesPanel>
        </Wrapper>
      </Route>
    </MemoryRouter>
  );
};

EditMode.parameters = {
  msw: [
    rest.get('/api/process-instances/:processInstanceId', (_, res, ctx) => {
      return res(ctx.json(mockActiveInstance));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/variables',
      (_, res, ctx) => {
        return res(ctx.json(mockBigVariables));
      }
    ),
  ],
};

const WithActiveOperations: Story = () => {
  useEffect(() => {
    flowNodeSelectionStore.init();
    currentInstanceStore.fetchCurrentInstance(1);
    variablesStore.init('1');
    variablesStore.shouldCancelOngoingRequests = false;

    return () => {
      currentInstanceStore.reset();
      flowNodeSelectionStore.reset();
      variablesStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={['/instances/6755399441055885']}>
      <Route path="/instances/:processInstanceId">
        <Wrapper>
          <Styled.VariablesPanel>
            <Form onSubmit={() => {}}>
              {({handleSubmit}) => {
                return (
                  <form onSubmit={handleSubmit}>
                    <PendingVariableWrapper />
                  </form>
                );
              }}
            </Form>
          </Styled.VariablesPanel>
        </Wrapper>
      </Route>
    </MemoryRouter>
  );
};

WithActiveOperations.parameters = {
  msw: [
    rest.get('/api/process-instances/:processInstanceId', (_, res, ctx) => {
      return res(ctx.json(mockActiveInstance));
    }),
    rest.post(
      '/api/process-instances/:processInstanceId/variables',
      (_, res, ctx) => {
        return res(ctx.json(mockVariablesWithActiveOperation));
      }
    ),
  ],
};

export {AddMode, EditMode, WithActiveOperations};
