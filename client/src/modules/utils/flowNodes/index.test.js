/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isFlowNode, getFlowNodes} from './index';

const createElement = (type, isFlowNode, id) => {
  return {
    id,
    businessObject: {
      $type: type,
      $instanceOf: () => isFlowNode,
    },
  };
};

describe('flowNodes', () => {
  describe('isFlowNode', () => {
    it('should return true for tasks', () => {
      const element = createElement('bpmn:Task', true);

      expect(isFlowNode(element)).toBeTruthy();
    });

    it('should return false for Processes', () => {
      const element = createElement('bpmn:Process', false);

      expect(isFlowNode(element)).toBeFalsy();
    });
  });

  describe('getFlowNodes', () => {
    it('should get flow nodes', () => {
      const Task1 = createElement('bpmn:Task', true, 'Task1');
      const Root = createElement('bpmn:Process', false, 'Root');
      const SequenceFlow1 = createElement(
        'bpmn:SequenceFlow',
        false,
        'SequenceFlow1'
      );

      const elements = [Task1, Root, SequenceFlow1];

      expect(getFlowNodes(elements)).toEqual({
        flowNodes: [Task1],
        ids: [Task1.id],
      });
    });

    it('sould get empty objects', () => {
      expect(getFlowNodes([])).toEqual({ids: [], flowNodes: []});
      expect(getFlowNodes()).toEqual({ids: [], flowNodes: []});
    });
  });
});
