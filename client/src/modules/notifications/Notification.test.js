import React from 'react';
import {shallow} from 'enzyme';

import Notification from './Notification';

jest.useFakeTimers();

it('should render text provided in config', () => {
  const node = shallow(<Notification config={{text: 'Sample', stayOpen: true}} />);

  expect(node).toMatchSnapshot();
});

it('should call the provided remove function after a while', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample'}} remove={spy} />);

  jest.runAllTimers();

  expect(spy).toHaveBeenCalled();
});

it('should not remove the Notification if it should stay open', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample', stayOpen: true}} remove={spy} />);

  jest.runAllTimers();

  expect(spy).not.toHaveBeenCalled();
});

it('should not remove the Notification if the user interacts with it', () => {
  const spy = jest.fn();
  const node = shallow(<Notification config={{text: 'Sample', stayOpen: true}} remove={spy} />);

  node.simulate('click');

  jest.runAllTimers();

  expect(spy).not.toHaveBeenCalled();
});

it('should remove the Notification after the specified delay', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample', duration: 1234}} remove={spy} />);

  jest.runTimersToTime(1230);
  expect(spy).not.toHaveBeenCalled();
  jest.runTimersToTime(1235);
  expect(spy).toHaveBeenCalled();
});
