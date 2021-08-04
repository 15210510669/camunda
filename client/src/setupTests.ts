/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom/extend-expect';
import {clearClientCache} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';

beforeAll(() => {
  mockServer.listen({
    onUnhandledRequest(req) {
      console.error(
        'Found an unhandled %s request to %s',
        req.method,
        req.url.href,
      );
    },
  });

  Object.defineProperty(window, 'clientConfig', {
    value: {
      ...window.clientConfig,
      canLogout: true,
    },
  });
});
beforeEach(async () => {
  await clearClientCache();
});
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());

// mock app version
process.env.REACT_APP_VERSION = '1.2.3';

jest.mock('@camunda-cloud/common-ui-react', () => ({
  CmNotificationContainer: () => {
    return null;
  },
}));
