/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from '@wojtekmaj/enzyme-adapter-react-17';
import 'jest-enzyme';
import './modules/polyfills/array_flat';
import {initTranslation} from 'translation';
import * as request from 'request';
import translation from '../../backend/src/main/resources/localization/en.json';

Enzyme.configure({adapter: new Adapter()});
document.execCommand = jest.fn();

beforeAll(async () => {
  jest.spyOn(request, 'get').mockImplementationOnce(async (url) => ({json: () => translation}));
  await initTranslation();
});

global.MutationObserver = class MutationObserver {
  observe() {}
};

// since jest does not offer an out of the box way to flush promises:
// https://github.com/facebook/jest/issues/2157
global.flushPromises = () => new Promise((resolve) => setImmediate(resolve));
