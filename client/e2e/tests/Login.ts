/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {screen} from '@testing-library/testcafe';
import {convertToQueryString} from './utils/convertToQueryString';
import {getPathname} from './utils/getPathname';
import {getSearch} from './utils/getSearch';
import {USE_NEW_APP_HEADER} from '../../src/modules/feature-flags';

fixture('Login')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await t.maximizeWindow();
  });

test('Log in with invalid user account', async (t) => {
  await t
    .expect(screen.queryByLabelText('Password').getAttribute('type'))
    .eql('password');

  await t
    .typeText(screen.queryByLabelText('Username'), 'demo')
    .typeText(screen.queryByLabelText('Password'), 'wrong-password')
    .click(screen.queryByRole('button', {name: 'Log in'}));
  await t
    .expect(screen.queryByText('Username and Password do not match').exists)
    .ok();
  await t.expect(await getPathname()).eql('/login');
});

test('Log in with valid user account', async (t) => {
  await t
    .typeText(screen.queryByLabelText('Username'), 'demo')
    .typeText(screen.queryByLabelText('Password'), 'demo')
    .click(screen.queryByRole('button', {name: 'Log in'}));

  await t.expect(await getPathname()).eql('/');
});

(USE_NEW_APP_HEADER ? test.skip : test)('Log out', async (t) => {
  await t
    .typeText(screen.queryByLabelText('Username'), 'demo')
    .typeText(screen.queryByLabelText('Password'), 'demo')
    .click(screen.queryByRole('button', {name: 'Log in'}));

  await t
    .click(screen.queryByRole('button', {name: /demo/i}))
    .click(screen.queryByRole('button', {name: 'Logout'}));

  await t.expect(await getPathname()).eql('/login');
});

(USE_NEW_APP_HEADER ? test : test.skip)('Log out', async (t) => {
  await t
    .typeText(screen.queryByLabelText('Username'), 'demo')
    .typeText(screen.queryByLabelText('Password'), 'demo')
    .click(screen.queryByRole('button', {name: 'Log in'}));

  await t
    .click(screen.queryByLabelText('Settings', {selector: 'button'}))
    .click(screen.queryByRole('button', {name: 'Log out'}));

  await t.expect(await getPathname()).eql('/login');
});

test('Redirect to initial page after login', async (t) => {
  await t.expect(await getPathname()).eql('/login');
  await t.navigateTo('/processes?active=true&incidents=true');
  await t.expect(await getPathname()).eql('/login');

  await t
    .typeText(screen.queryByLabelText('Username'), 'demo')
    .typeText(screen.queryByLabelText('Password'), 'demo')
    .click(screen.queryByRole('button', {name: 'Log in'}));

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});
