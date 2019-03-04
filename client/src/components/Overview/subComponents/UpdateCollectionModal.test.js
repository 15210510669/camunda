import React from 'react';
import {shallow} from 'enzyme';

import UpdateCollectionModal from './UpdateCollectionModal';
import {Input} from 'components';

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport]
  }
};

const props = {
  collection,
  onClose: jest.fn(),
  onConfirm: jest.fn()
};

it('should add isInvalid prop to the name input if name is empty', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);
  await node.instance().componentDidMount();

  await node.setState({
    name: ''
  });

  expect(node.find(Input).props()).toHaveProperty('isInvalid', true);
});

it('should provide name edit input', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);
  node.setState({name: 'test name'});

  expect(node.find(Input)).toBePresent();
});

it('have a cancel and save collection button', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);

  expect(node.find('.confirm')).toBePresent();
  expect(node.find('.cancel')).toBePresent();
});

it('should invoke onConfirm on save button click', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith({name: 'aCollectionName'});
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);
  node.setState({name: ''});

  expect(node.find('.confirm')).toBeDisabled();
});

it('should update name on input change', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);
  node.setState({name: 'test name'});

  const input = 'asdf';
  node.find(Input).simulate('change', {target: {value: input}});
  expect(node.state().name).toBe(input);
});

it('should invoke onClose on cancel', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);

  await node.find('.cancel').simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});

it('should select the name input field when the component is mounted', async () => {
  const node = await shallow(<UpdateCollectionModal {...props} />);

  const input = {focus: jest.fn(), select: jest.fn()};
  node.instance().inputRef(input);

  await node.instance().componentDidMount();

  expect(input.focus).toHaveBeenCalled();
  expect(input.select).toHaveBeenCalled();
});
