import React from 'react';
import {shallow} from 'enzyme';

import TargetValueComparison from './TargetValueComparison';

const validProps = {
  reportResult: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        operation: 'avg',
        property: 'duration'
      },
      groupBy: {
        type: 'flowNodes'
      },
      visualization: 'heat'
    },
    result: {}
  },
  configuration: {
    targetValue: {
      active: false,
      values: {
        a: {
          value: 12,
          unit: 'days'
        }
      }
    }
  }
};

const validPropsWithoutTargetValues = {
  reportResult: validProps.reportResult,
  configuration: {
    targetValue: {
      active: false,
      values: {}
    }
  }
};

const invalidProps = {
  reportResult: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        operation: 'avg',
        property: 'duration'
      },
      groupBy: {
        type: 'None'
      },
      visualization: 'heat'
    },
    result: {}
  },
  configuration: {
    active: false,
    values: {
      a: {
        value: 12,
        unit: 'days'
      }
    }
  }
};

it('should display a double button', () => {
  const node = shallow(<TargetValueComparison {...invalidProps} />);

  expect(node.find('.TargetValueComparison__toggleButton')).toBePresent();
  expect(node.find('.TargetValueComparison__editButton')).toBePresent();
});

it('should toggle the mode with the left button', () => {
  const spy = jest.fn();
  const node = shallow(<TargetValueComparison {...validProps} onChange={spy} />);

  node.find('.TargetValueComparison__toggleButton').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].configuration.targetValue.active).toBe(true);
});

it('should open the modal with the left button if there are no target values set', async () => {
  const node = shallow(<TargetValueComparison {...validPropsWithoutTargetValues} />);

  await node.find('.TargetValueComparison__toggleButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('should open the target value edit modal on with the right button', async () => {
  const node = shallow(<TargetValueComparison {...validProps} />);

  await node.find('.TargetValueComparison__editButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('it should toggle target value view mode off if no target values are defined', async () => {
  const spy = jest.fn();
  const node = shallow(<TargetValueComparison {...validProps} onChange={spy} />);

  node.instance().confirmModal({});

  expect(spy).toHaveBeenCalledWith({
    configuration: {
      targetValue: {
        active: false,
        values: {}
      }
    }
  });
});
