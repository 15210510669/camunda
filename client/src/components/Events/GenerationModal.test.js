/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {GenerationModal} from './GenerationModal';
import EventsSourceModal from './EventsSourceModal';
import {createProcess} from './service';
import {Button} from 'components';

jest.mock('./service', () => ({createProcess: jest.fn().mockReturnValue('processId')}));
const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should add/remove a source from the list', () => {
  const node = shallow(<GenerationModal {...props} />);

  node.find('EntityList').prop('action').props.onClick({});

  node.find(EventsSourceModal).prop('onConfirm')([{type: 'external'}]);

  expect(node.find('EntityList').prop('data')[0].name).toBe('all events');

  node.find('EntityList').prop('data')[0].actions[0].action();

  expect(node.find('EntityList').prop('data').length).toBe(0);
});

it('should redirect to the process view on confirmation', () => {
  const node = shallow(<GenerationModal {...props} />);

  node.find('EntityList').prop('action').props.onClick({});
  node.find(EventsSourceModal).prop('onConfirm')([{type: 'external'}]);
  node.find(Button).at(1).simulate('click');

  expect(createProcess).toHaveBeenCalledWith({
    autogenerate: true,
    eventSources: [{type: 'external'}],
  });

  expect(node.find('Redirect')).toExist();
  expect(node.props().to).toEqual('/eventBasedProcess/processId/generated');
});
