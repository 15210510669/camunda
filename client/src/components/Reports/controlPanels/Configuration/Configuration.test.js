import React from 'react';
import {shallow} from 'enzyme';

import Configuration from './Configuration';
import {typeA, typeB} from './visualizations';
import {Button} from 'components';

jest.mock('./visualizations', () => {
  const typeA = () => null;
  typeA.defaults = {
    propA: 'abc',
    propB: 1
  };
  typeA.onUpdate = jest.fn().mockReturnValue({prop: 'updateValue'});

  const typeB = () => null;
  typeB.defaults = {
    propC: false
  };

  const typeC = () => null;
  typeC.defaults = jest.fn().mockReturnValue({propD: 20});

  return {typeA, typeB, typeC, bar: typeA, line: typeA};
});

it('should be disabled if no type is set', () => {
  const node = shallow(<Configuration report={{data: {configuration: {}}}} />);

  expect(node.find('Popover')).toBeDisabled();
});

it('should be disabled if the report is combined with a duration view', () => {
  const node = shallow(
    <Configuration
      report={{
        combined: true,
        data: {reports: [{id: 'test'}]},
        result: {test: {data: {view: {property: 'duration'}}}}
      }}
    />
  );

  expect(node.find('Popover')).toBeDisabled();
});

it('should contain the Component from the visualizations based on the type', () => {
  const node = shallow(
    <Configuration report={{data: {configuration: {}}}} type="typeA" onChange={() => {}} />
  );

  expect(node.find(typeA)).toBePresent();

  node.setProps({type: 'typeB'});

  expect(node.find(typeA)).not.toBePresent();
  expect(node.find(typeB)).toBePresent();
});

it('should reset to defaults', () => {
  const spy = jest.fn();
  const node = shallow(
    <Configuration
      report={{data: {configuration: {}}}}
      type="typeA"
      onChange={spy}
      configuration={{}}
    />
  );

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].configuration.precision).toEqual({$set: null});
});
