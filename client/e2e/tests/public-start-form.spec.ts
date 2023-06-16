/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../axe-test';
import * as zeebeClient from '../zeebeClient';

test.describe('public start process', () => {
  test('should submit form', async ({page, makeAxeBuilder}) => {
    await zeebeClient.deploy(['./e2e/resources/subscribeFormProcess.bpmn']);
    await page.goto('/new/subscribeFormProcess');

    await expect(page.getByLabel('Name')).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);

    await page.getByLabel('Name').fill('Joe Doe');
    await page.getByLabel('Email').fill('joe@doe.com');
    await page.getByRole('button', {name: 'Submit'}).click();

    await expect(
      page.getByRole('heading', {
        name: 'Success!',
      }),
    ).toBeVisible();
  });
});
