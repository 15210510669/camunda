/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Select} from 'components';

import {DistributedBy} from './DistributedBy';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadVariables: jest.fn().mockReturnValue([
    {name: 'var1', type: 'String'},
    {name: 'var2', type: 'Integer'},
    {name: 'var3', type: 'Boolean'},
  ]),
}));

const data = {
  visualization: 'heat',
  view: {entity: 'userTask'},
  configuration: {distributedBy: {}},
  groupBy: {type: 'userTasks'},
};

it('should match snapshot', () => {
  const node = shallow(<DistributedBy report={{data}} />);

  expect(node).toMatchSnapshot();
});

it('should change the visualization if it is incompatible with the new configuration', () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      report={{
        data: {
          ...data,
          groupBy: {type: 'assignee'},
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({value: 'userTask'})).toExist();

  node.find(Select).prop('onChange')('userTask');

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {distributedBy: {$set: {type: 'userTask', value: null}}},
      visualization: {$set: 'bar'},
    },
    true
  );
});

it('should offer the correct options based on the group by type', () => {
  const spy = jest.fn();
  let node = shallow(
    <DistributedBy
      report={{
        data: {
          ...data,
          groupBy: {type: 'userTasks'},
        },
      }}
      onChange={spy}
    />
  );

  expect(node.find({value: 'assignee'})).toExist();
  expect(node.find({value: 'candidateGroup'})).toExist();
  expect(node.find({value: 'userTask'})).not.toExist();

  node.setProps({
    report: {
      data: {
        ...data,
        groupBy: {type: 'startDate'},
      },
    },
  });

  expect(node.find({value: 'assignee'})).toExist();
  expect(node.find({value: 'candidateGroup'})).toExist();
  expect(node.find({value: 'userTask'})).toExist();

  node.setProps({
    report: {
      data: {
        ...data,
        groupBy: {type: 'assignee'},
      },
    },
  });

  expect(node.find({value: 'assignee'})).not.toExist();
  expect(node.find({value: 'candidateGroup'})).not.toExist();
  expect(node.find({value: 'userTask'})).toExist();
});

it('should load and render variables for process instance count reports', () => {
  let node = shallow(
    <DistributedBy
      mightFail={jest.fn().mockImplementation((data, cb) => cb(data))}
      report={{
        data: {
          ...data,
          view: {entity: 'processInstance'},
          groupBy: {type: 'startDate'},
        },
      }}
    />
  );

  runLastEffect();

  expect(node).toMatchSnapshot();
});

it('should invoke onChange with the selected variable', () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      mightFail={jest.fn().mockImplementation((data, cb) => cb(data))}
      report={{
        data: {
          ...data,
          view: {entity: 'processInstance'},
          groupBy: {type: 'startDate'},
        },
      }}
      onChange={spy}
    />
  );
  runLastEffect();

  node.find(Select).prop('onChange')('var1');

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {
        distributedBy: {$set: {type: 'variable', value: {name: 'var1', type: 'String'}}},
      },
      visualization: {$set: 'bar'},
    },
    true
  );
});

it('should reset process part if defined when selection an option', () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      report={{
        data: {
          ...data,
          configuration: {...data.configuration, processPart: {}},
        },
      }}
      onChange={spy}
    />
  );

  node.find(Select).prop('onChange')('assignee');

  expect(spy).toHaveBeenCalledWith(
    {
      configuration: {
        distributedBy: {$set: {type: 'assignee', value: null}},
        processPart: {$set: null},
      },
      visualization: {$set: 'bar'},
    },
    true
  );
});
