/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';
import {loadConfig} from 'config';

import {updateTelemetry} from './service';

import {TelemetrySettings} from './TelemetrySettings';

jest.mock('config', () => ({
  isMetadataTelemetryEnabled: jest.fn().mockReturnValue(true),
  loadConfig: jest.fn(),
}));

jest.mock('notifications', () => ({addNotification: jest.fn()}));

jest.mock('./service', () => ({
  updateTelemetry: jest.fn(),
}));

const props = {
  onClose: jest.fn(),
  mightFail: (data, cb) => cb(data),
};

it('should contain a checkbox with the current state of the telemetry settings', async () => {
  const node = shallow(<TelemetrySettings {...props} />);

  await runLastEffect();

  expect(node.find(LabeledInput).prop('checked')).toBe(true);
});

it('should update the telemetry when applying the changes', async () => {
  const node = shallow(<TelemetrySettings {...props} />);

  await runLastEffect();

  node.find(LabeledInput).simulate('change', {target: {checked: false}});
  node.find('[primary]').simulate('click');

  expect(updateTelemetry).toHaveBeenCalledWith(false);
  expect(loadConfig).toHaveBeenCalled();
});
