/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Table, Switch} from 'components';

import {loadEvents} from './service';
import EventTableWithErrorHandling from './EventTable';

const EventTable = EventTableWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadEvents: jest.fn().mockReturnValue([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10
    },
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderAccepted',
      count: 10
    }
  ]),
  isNonTimerEvent: jest.fn().mockReturnValue(false)
}));

jest.mock('debounce', () => fn => fn);

const props = {
  selection: {id: 'a', $instanceOf: type => type === 'bpmn:Event'},
  mappings: {
    a: {
      start: null,
      end: {
        group: 'eventGroup',
        source: 'order-service',
        eventName: 'OrderProcessed'
      }
    }
  },
  onMappingChange: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  xml: 'some xml',
  eventSources: [{type: 'external'}],
  onSelectEvent: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<EventTable {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load events', () => {
  loadEvents.mockClear();
  shallow(<EventTable {...props} />);

  expect(loadEvents).toHaveBeenCalled();
});

it('should disable table if no node is selected', () => {
  const node = shallow(<EventTable {...props} selection={null} />);

  expect(node.find(Table).prop('body')[0].props.className).toContain('disabled');
});

it('should allow searching for events', () => {
  const node = shallow(<EventTable {...props} />);

  node.setState({showSuggested: false});

  loadEvents.mockClear();

  node.find('.searchInput').prop('onChange')({target: {value: 'some String'}});

  expect(loadEvents).toHaveBeenCalledWith({eventSources: props.eventSources}, 'some String');
});

it('should call callback when changing mapping', () => {
  const spy = jest.fn();
  const node = shallow(<EventTable {...props} onMappingChange={spy} />);

  node
    .find(Table)
    .prop('body')[0]
    .content[0].props.onChange({target: {checked: false}});

  expect(spy).toHaveBeenCalledWith(
    {group: 'eventGroup', source: 'order-service', eventName: 'OrderProcessed'},
    false
  );
});

it('should pass payload to backend when loading events for suggestions', () => {
  loadEvents.mockClear();
  shallow(<EventTable {...props} />);

  expect(loadEvents).toHaveBeenCalledWith(
    {
      targetFlowNodeId: 'a',
      xml: 'some xml',
      mappings: props.mappings,
      eventSources: props.eventSources
    },
    ''
  );
});

it('should load updated suggestions when the selection changes', () => {
  const node = shallow(<EventTable {...props} />);

  loadEvents.mockClear();

  node.setProps({selection: {id: 'b', $instanceOf: type => type === 'bpmn:Event'}});

  expect(loadEvents).toHaveBeenCalled();
});

it('should not reload events if suggestions are not activated', () => {
  const node = shallow(<EventTable {...props} />);
  node.find(Switch).prop('onChange')({target: {checked: false}});

  loadEvents.mockClear();

  node.setProps({selection: {id: 'b', $instanceOf: type => type === 'bpmn:Event'}});

  expect(loadEvents).not.toHaveBeenCalled();
});

it('should mark suggested events', () => {
  loadEvents.mockReturnValueOnce([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10,
      suggested: true
    },
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderAccepted',
      count: 10,
      suggested: false
    }
  ]);

  const node = shallow(<EventTable {...props} />);

  const events = node.find(Table).prop('body');
  expect(events[0].props.className).toContain('suggested');
  expect(events[1].props.className).not.toContain('suggested');
});

it('should disable events Suggestion if there are any camunda event sources', () => {
  const node = shallow(<EventTable {...props} eventSources={[{type: 'camunda'}]} />);

  expect(node.find('Switch')).toBeDisabled();
});

it('should not show events from hidden sources in the table', () => {
  loadEvents.mockReturnValueOnce([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10
    },
    {
      group: 'bookrequest',
      source: 'camunda',
      eventName: 'startEvent',
      count: 10
    }
  ]);

  const node = shallow(
    <EventTable
      {...props}
      eventSources={[{type: 'external', hidden: true}, {processDefinitionKey: 'bookrequest'}]}
    />
  );

  const events = node.find(Table).prop('body');
  expect(events).toHaveLength(1);
  expect(events[0].content).toContain('bookrequest');
});

it('should invoke onSelectEvent when clicking on an element', () => {
  const node = shallow(<EventTable {...props} />);

  const events = node.find(Table).prop('body');
  events[0].props.onClick({target: {getAttribute: () => null, closest: () => null}});

  expect(props.onSelectEvent).toHaveBeenCalledWith({
    count: 10,
    eventName: 'OrderProcessed',
    group: 'eventGroup',
    source: 'order-service'
  });
});

it('should reset the selected event when clicking on the checkbox', () => {
  const node = shallow(<EventTable {...props} />);

  const events = node.find(Table).prop('body');
  events[0].props.onClick({target: {getAttribute: () => 'checkbox'}});

  expect(props.onSelectEvent).toHaveBeenCalledWith(null);
});

it('Should collapse the table on collapse button click', () => {
  const node = shallow(<EventTable {...props} />);
  node.find('.collapseButton').simulate('click');

  expect(node.find(Table).hasClass('collapsed')).toBe(true);
});
