/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {
  mockResolvedAsyncFn,
  createInstance,
  createOperation,
  flushPromises
} from 'modules/testUtils';

import {STATE, OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import Actions from './Actions';
import ActionStatus from 'modules/components/ActionStatus';
import ActionItems from './ActionItems';

import * as api from 'modules/api/instances/instances';

// mocking api
api.applyOperation = mockResolvedAsyncFn({count: 1, reason: null});

describe('Actions', () => {
  let mockOperation, mockInstance, onButtonClick;

  it('should match snapshots', () => {
    // when
    mockOperation = createOperation({state: OPERATION_STATE.SCHEDULED});
    mockInstance = createInstance({operations: [mockOperation]});
    let node = shallow(<Actions instance={mockInstance} />);
    //then
    expect(node).toMatchSnapshot();

    // when
    mockOperation = createOperation({state: OPERATION_STATE.FAILED});
    mockInstance = createInstance({operations: [mockOperation]});
    node = shallow(<Actions instance={mockInstance} />);

    // then
    expect(node).toMatchSnapshot();
  });

  it('should pass props ActionStatus', () => {
    // when
    mockOperation = createOperation({
      state: OPERATION_STATE.SCHEDULED,
      type: OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    });
    mockInstance = createInstance({operations: [mockOperation]});

    const node = shallow(<Actions instance={mockInstance} />);

    //then
    expect(node.find(ActionStatus).props().operationState).toBe(
      OPERATION_STATE.SCHEDULED
    );

    expect(node.find(ActionStatus).props().operationType).toBe(
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    );
  });

  describe('Action Buttons', () => {
    it('should render action buttons for active instance', () => {
      // when
      mockInstance = createInstance({state: STATE.ACTIVE, operations: []});
      const node = shallow(<Actions instance={mockInstance} />);
      const ActionItemsNode = node.find(ActionItems);
      const Button = ActionItemsNode.find(ActionItems.Item);

      // then
      expect(ActionItemsNode).toExist();
      expect(Button.length).toEqual(1);
      expect(Button.props().type).toEqual(
        OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
      );
    });

    it('should render action buttons for instance with incidents', () => {
      // when
      mockInstance = createInstance({state: STATE.INCIDENT, operations: []});
      const node = shallow(<Actions instance={mockInstance} />);
      const ActionItemsNode = node.find(ActionItems);

      // then
      expect(ActionItemsNode).toExist();
      expect(ActionItemsNode.find(ActionItems.Item).length).toEqual(2);
      expect(
        ActionItemsNode.find(ActionItems.Item)
          .at(0)
          .props().type
      ).toEqual(OPERATION_TYPE.RESOLVE_INCIDENT);
      expect(
        ActionItemsNode.find(ActionItems.Item)
          .at(1)
          .props().type
      ).toEqual(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE);
    });

    describe('Retry', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: STATE.INCIDENT});
        onButtonClick = jest.fn();
      });
      afterEach(() => {
        jest.clearAllMocks();
      });

      it('should handle retry of instance incident ', async () => {
        //given
        const node = shallow(
          <Actions instance={mockInstance} onButtonClick={onButtonClick} />
        );
        const actionItem = node.find(ActionItems.Item).at(0);

        // when
        actionItem.simulate('click');

        // await for operation response
        await flushPromises();
        node.update();

        expect(api.applyOperation).toBeCalledWith(
          mockInstance.id,
          OPERATION_TYPE.RESOLVE_INCIDENT
        );
        // expect Spinner to appear
        expect(node.find(ActionStatus).props().operationState).toEqual(
          OPERATION_STATE.SCHEDULED
        );

        // expect callback to be called
        expect(onButtonClick).toHaveBeenCalled();
      });
    });

    describe('Cancel', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: STATE.ACTIVE});
        onButtonClick = jest.fn();
      });
      afterEach(() => {
        jest.clearAllMocks();
      });

      it('should handle the cancelation of an instance ', async () => {
        //given
        const node = shallow(
          <Actions instance={mockInstance} onButtonClick={onButtonClick} />
        );
        const actionItem = node.find(ActionItems.Item);

        // when
        actionItem.simulate('click');
        await flushPromises();
        node.update();

        // then
        expect(api.applyOperation).toBeCalledWith(
          mockInstance.id,
          OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
        );

        // expect Spinner to appear
        expect(node.find(ActionStatus).props().operationState).toEqual(
          OPERATION_STATE.SCHEDULED
        );

        // expect callback to be called
        expect(onButtonClick).toHaveBeenCalled();
      });
    });
  });
});
