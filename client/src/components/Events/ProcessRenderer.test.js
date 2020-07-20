/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ProcessRenderer from './ProcessRenderer';
import Modeler from 'bpmn-js/lib/Modeler';

jest.mock('react', () => ({...jest.requireActual('react'), useEffect: (fn) => fn()}));

it('should call onChange with the xml', async () => {
  const viewer = new Modeler();
  const spy = jest.fn();

  shallow(<ProcessRenderer viewer={viewer} onChange={spy} />);

  viewer.eventBus.on.mock.calls[0][1]();

  expect(viewer.saveXML).toHaveBeenCalled();

  await flushPromises();

  expect(spy).toHaveBeenCalledWith('some xml', false);
});

it('should set the the change as remove is shape is removed', async () => {
  const viewer = new Modeler();
  const spy = jest.fn();
  shallow(<ProcessRenderer viewer={viewer} onChange={spy} />);

  // shape.remove
  viewer.eventBus.on.mock.calls[1][1]();

  // commandStack.changed
  viewer.eventBus.on.mock.calls[0][1]();

  await flushPromises();

  expect(spy).toHaveBeenCalledWith('some xml', true);
});

it('should create mapping overlays', () => {
  const viewer = new Modeler();

  const props = {
    viewer,
    mappings: {a: {start: {eventName: '1'}, end: null}},
  };

  shallow(<ProcessRenderer {...props} />);

  expect(viewer.overlays.add).toHaveBeenCalled();
});
