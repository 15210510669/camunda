/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';
import {getOptimizeProfile} from 'config';

import {UsersModal} from './UsersModal';
import {updateUsers} from './service';

jest.mock('./service', () => ({
  updateUsers: jest.fn(),
  getUsers: jest.fn().mockReturnValue([]),
}));

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

beforeEach(() => updateUsers.mockClear());

const props = {
  id: 'processId',
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should disable the save button if the user list is empty', () => {
  const node = shallow(<UsersModal {...props} />);

  expect(node.find(UserTypeahead).prop('users').length).toBe(0);
  expect(node.find('[primary]')).toBeDisabled();
});

it('should update the list of users based on the UserTypeahead', () => {
  const node = shallow(<UsersModal {...props} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}},
    {id: 'GROUP:sales', identity: {id: 'sales', memberCount: '2', name: 'Sales', type: 'group'}},
  ]);

  node.find('[primary]').simulate('click');

  expect(updateUsers).toHaveBeenCalledWith('processId', [
    {id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}},
    {id: 'GROUP:sales', identity: {id: 'sales', memberCount: '2', name: 'Sales', type: 'group'}},
  ]);
});

it('should disable custom input in cloud mode', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = await shallow(<UsersModal {...props} />);

  expect(node.find(UserTypeahead).prop('optionsOnly')).toBe(true);
});
