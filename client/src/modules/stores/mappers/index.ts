/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isFlowNode} from 'modules/utils/flowNodes';
import {
  TYPE,
  FLOWNODE_TYPE_HANDLE,
  MULTI_INSTANCE_TYPE,
} from 'modules/constants';

const getSelectableFlowNodes = (bpmnElements: any) => {
  if (!bpmnElements) {
    return {};
  }

  return Object.entries(bpmnElements).reduce(
    (accumulator, [key, bpmnElement]) => {
      if (isFlowNode(bpmnElement)) {
        return {...accumulator, [key]: bpmnElement};
      }
      return accumulator;
    },
    {}
  );
};

const createNodeMetaDataMap = (bpmnElements: any) => {
  return Object.entries(bpmnElements).reduce(
    (map, [activityId, bpmnElement]) => {
      map.set(activityId, {
        // @ts-expect-error ts-migrate(2571) FIXME: Object is of type 'unknown'.
        name: bpmnElement.name,
        type: {
          elementType: getElementType(bpmnElement),
          eventType: getEventType(bpmnElement),
          multiInstanceType: getMultiInstanceType(bpmnElement),
        },
      });

      return map;
    },
    new Map()
  );
};

const getEventType = (bpmnElement: any) => {
  // doesn't return a event type when element of type 'multiple event'
  if (bpmnElement.eventDefinitions?.length === 1) {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    return FLOWNODE_TYPE_HANDLE[bpmnElement.eventDefinitions[0].$type];
  }
};

const getElementType = (bpmnElement: any) => {
  const {$type: type, cancelActivity, triggeredByEvent} = bpmnElement;

  if (type === 'bpmn:SubProcess' && triggeredByEvent) {
    return TYPE.EVENT_SUBPROCESS;
  }
  if (type === 'bpmn:BoundaryEvent') {
    return cancelActivity === false
      ? TYPE.EVENT_BOUNDARY_NON_INTERRUPTING
      : TYPE.EVENT_BOUNDARY_INTERRUPTING;
  } else {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    return FLOWNODE_TYPE_HANDLE[type];
  }
};

const getMultiInstanceType = (bpmnElement: any) => {
  if (!bpmnElement.loopCharacteristics) {
    return;
  }

  return bpmnElement.loopCharacteristics.isSequential
    ? MULTI_INSTANCE_TYPE.SEQUENTIAL
    : MULTI_INSTANCE_TYPE.PARALLEL;
};

const getProcessedSequenceFlows = (sequenceFlows: any) => {
  return sequenceFlows
    .map((item: any) => item.activityId)
    .filter(
      (value: any, index: any, self: any) => self.indexOf(value) === index
    );
};

const mapify = (arrayOfObjects: any, uniqueKey: any) => {
  if (arrayOfObjects === undefined) return new Map();
  return arrayOfObjects.reduce((acc: any, object: any) => {
    return acc.set(object[uniqueKey], object);
  }, new Map());
};

const addFlowNodeName = (object: any, nodeMetaData: any) => {
  const modifiedObject = {...object};
  modifiedObject.flowNodeName =
    (nodeMetaData && nodeMetaData.name) || object.flowNodeId;
  return modifiedObject;
};

export {
  getSelectableFlowNodes,
  createNodeMetaDataMap,
  getProcessedSequenceFlows,
  mapify,
  addFlowNodeName,
};
