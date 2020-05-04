/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {ThemeProvider} from 'modules/theme';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {HashRouter as Router} from 'react-router-dom';
import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';
import {InstanceSelectionProvider} from 'modules/contexts/InstanceSelectionContext';

import {
  emptyList,
  mockProps,
  mockPropsWithInstances,
  mockPropsWithNoOperation,
  mockPropsBeforeDataLoaded,
  mockPropsWithPoll,
  ACTIVE_INSTANCE,
} from './ListPanel.setup';

import ListPanel from './ListPanel';
import List from './List';
import ListFooter from './ListFooter';

import * as api from 'modules/api/instances/instances';

jest.mock('modules/utils/bpmn');

// api mocks
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

describe('ListPanel', () => {
  let Component, ComponentWithInstances, ComponentBeforeDataLoaded;
  let dataManager;
  beforeEach(() => {
    jest.clearAllMocks();
    dataManager = createMockDataManager();

    Component = (
      <ListPanel.WrappedComponent {...mockProps} {...{dataManager}} />
    );
    ComponentWithInstances = (
      <ListPanel.WrappedComponent
        {...mockPropsWithInstances}
        {...{dataManager}}
      />
    );
    ComponentBeforeDataLoaded = (
      <ListPanel.WrappedComponent
        {...mockPropsBeforeDataLoaded}
        {...{dataManager}}
      />
    );
  });

  it('should have initially default state', () => {
    // given
    const node = shallow(Component);
    // then
    expect(node.state().entriesPerPage).toBe(0);
  });

  describe('messages', () => {
    it('should display a message for empty list when filter has no state', async () => {
      const node = shallow(
        <List {...emptyList} filter={{error: 'mock error message'}} />
      );

      expect(
        node.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });

    it('should display a empty list message when filter has at least one state', async () => {
      const node = shallow(
        <List
          {...emptyList}
          filter={{error: 'mock error message', active: true}}
        />
      );

      expect(
        node.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });
  });

  describe('display instances List', () => {
    it('should render a skeleton', () => {
      // given
      const node = shallow(ComponentBeforeDataLoaded);

      const TBodyNode = node.find(List.Skeleton);
      expect(TBodyNode).toHaveLength(1);
    });

    it('should render table body', () => {
      // given
      const node = shallow(ComponentBeforeDataLoaded);

      // when
      node.setProps(mockPropsWithInstances);
      node.update();
      // TBody
      const TBodyNode = node.find(List.Body);
      expect(TBodyNode).toHaveLength(1);
    });

    it('should render Footer  when list is empty', () => {
      // given
      const node = shallow(Component);

      // then
      expect(node.find(ListFooter)).toExist();
    });

    it('should display the list and footer after the data is loaded', async () => {
      // given
      const node = shallow(ComponentWithInstances);

      node.setProps({instancesLoaded: true});
      node.update();

      // then
      expect(node.find(List)).toExist();
      expect(node.find(ListFooter)).toExist();
    });

    it('should pass a method to the footer to change the firstElement', async () => {
      // given

      const node = mount(
        <Router>
          <ThemeProvider>
            <DataManagerProvider>
              <CollapsablePanelProvider>
                <InstanceSelectionProvider>
                  <InstancesPollProvider>
                    <ListPanel.WrappedComponent
                      {...mockPropsWithNoOperation}
                      {...{dataManager}}
                    />
                  </InstancesPollProvider>
                </InstanceSelectionProvider>
              </CollapsablePanelProvider>
            </DataManagerProvider>
          </ThemeProvider>
        </Router>
      );

      // when data fetched
      await flushPromises();
      node.update();

      const changeFirstElement = node
        .find(ListFooter)
        .prop('onFirstElementChange');

      // then
      expect(changeFirstElement).toBeDefined();
    });

    it('should pass a method to the instances list to update the entries per page', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      node.setState({entriesPerPage: 8});
      const changeEntriesPerPage = node
        .find(List)
        .prop('onEntriesPerPageChange');

      // then
      expect(changeEntriesPerPage).toBeDefined();
      changeEntriesPerPage(87);
      expect(node.state('entriesPerPage')).toBe(87);
    });
  });

  describe('polling for instances changes', () => {
    beforeEach(() => {
      api.fetchWorkflowInstances.mockClear();
    });

    it('should not send ids for polling if no instances with active operations are displayed', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <DataManagerProvider>
              <CollapsablePanelProvider>
                <InstanceSelectionProvider>
                  <InstancesPollProvider>
                    <ListPanel.WrappedComponent
                      {...mockPropsWithNoOperation}
                      {...{dataManager}}
                    />
                  </InstancesPollProvider>
                </InstanceSelectionProvider>
              </CollapsablePanelProvider>
            </DataManagerProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();

      // no ids are sent for polling
      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    it('should send ids for polling if at least one instance with active operations is diplayed', async () => {
      const node = shallow(
        <ListPanel.WrappedComponent
          {...{...mockPropsWithPoll, instances: [ACTIVE_INSTANCE]}}
          {...{dataManager}}
        />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // then
      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith([
        ACTIVE_INSTANCE.id,
      ]);
    });

    it('should add the id to InstancesPollProvider after user starts operation on instance from list', async () => {
      // given
      const node = mount(
        <Router>
          <DataManagerProvider>
            <ThemeProvider>
              <CollapsablePanelProvider>
                <InstanceSelectionProvider>
                  <InstancesPollProvider>
                    <ListPanel.WrappedComponent
                      {...mockPropsWithPoll}
                      {...{dataManager}}
                    />
                  </InstancesPollProvider>
                </InstanceSelectionProvider>
              </CollapsablePanelProvider>
            </ThemeProvider>
          </DataManagerProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      const onOperationButtonClick = node.find(List).props()
        .onOperationButtonClick;

      onOperationButtonClick(node.find(List).props().data[0]);
      node.update();

      // then
      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith([
        node.find(List).props().data[0].id,
      ]);
    });

    it('should not poll for instances with active operations that are no longer in view after collapsing', async () => {
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setState({instancesLoaded: false});
      node.setState({instancesLoaded: true});

      // then
      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    // https://app.camunda.com/jira/browse/OPE-395
    it('should refetch instances when expanding the list panel', async () => {
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate load of instances in list
      node.setState({instancesLoaded: false});
      node.setState({instancesLoaded: true});

      node.setState({entriesPerPage: 3});
      await flushPromises();
      node.update();

      expect(dataManager.update).toHaveBeenCalledTimes(1);
    });
  });
});
