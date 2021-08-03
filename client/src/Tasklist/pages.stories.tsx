/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import React from 'react';
import {MemoryRouter, Route} from 'react-router-dom';

import {Tasklist} from './index';
import {Pages} from 'modules/constants/pages';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';
import {
  mockGetEmptyTasks,
  mockGetAllOpenTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
} from 'modules/queries/get-tasks';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskUnclaimedWithForm,
  mockGetTaskClaimedWithForm,
} from 'modules/queries/get-task';
import {mockGetForm} from 'modules/queries/get-form';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
} from 'modules/queries/get-task-variables';
import {
  mockGetSelectedVariables,
  mockGetSelectedVariablesEmptyVariables,
} from 'modules/queries/get-selected-variables';

export default {
  title: 'Pages States/Tasklist',
};

interface Props {
  children: React.ReactNode;
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'];
  mocks: React.ComponentProps<typeof MockedApolloProvider>['mocks'];
}

const Wrapper: React.FC<Props> = ({children, initialEntries, mocks}) => {
  return (
    <MockedApolloProvider mocks={mocks}>
      <MemoryRouter initialEntries={initialEntries}>
        <Route path={Pages.Initial({useIdParam: true})}>{children}</Route>
      </MemoryRouter>
    </MockedApolloProvider>
  );
};

const EmptyPage: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetEmptyTasks]}
      initialEntries={['/']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const AllOpenTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetAllOpenTasks()]}
      initialEntries={['/']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const ClaimedByMeTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetClaimedByMe]}
      initialEntries={['/?filter=claimed-by-me']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const UnclaimedTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetUnclaimed]}
      initialEntries={['/?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const CompletedTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetCompleted]}
      initialEntries={['/?filter=completed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const Unclaimed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimed(),
        mockGetTaskEmptyVariables(),
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const Claimed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskUnclaimed(),
        mockGetTaskEmptyVariables(),
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const Completed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetCompleted,
        mockGetTaskCompleted(),
        mockGetTaskEmptyVariables(),
        mockGetCompleted,
      ]}
      initialEntries={['/0?filter=completed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const UnclaimedWithVariables: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimed(),
        mockGetTaskVariables(),
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const ClaimedWithVariables: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskUnclaimed(),
        mockGetTaskVariables(),
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const UnclaimedWithForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariablesEmptyVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const UnclaimedWithPrefilledForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const UnclaimedWithNoEditPermission: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentRestrictedUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const ClaimedWithForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariablesEmptyVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const ClaimedWithPrefilledForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    >
      <Tasklist />
    </Wrapper>
  );
};

const ClaimedWithNoEditPermission: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentRestrictedUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    >
      <Tasklist />
    </Wrapper>
  );
};

export {
  EmptyPage,
  AllOpenTasks,
  ClaimedByMeTasks,
  UnclaimedTasks,
  CompletedTasks,
  Unclaimed,
  Claimed,
  Completed,
  UnclaimedWithVariables,
  ClaimedWithVariables,
  UnclaimedWithForm,
  UnclaimedWithPrefilledForm,
  UnclaimedWithNoEditPermission,
  ClaimedWithForm,
  ClaimedWithPrefilledForm,
  ClaimedWithNoEditPermission,
};
