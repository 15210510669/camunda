/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {devices, PlaywrightTestConfig} from '@playwright/test';

const IS_CI = Boolean(process.env.CI);
const IS_E2E = Boolean(process.env.IS_E2E);

/**
 * See https://playwright.dev/docs/test-configuration.
 */
const config: PlaywrightTestConfig = {
  testDir: './e2e',
  expect: {
    timeout: 10000,
  },
  fullyParallel: !IS_E2E,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI || IS_E2E ? 1 : undefined,
  reporter: IS_CI
    ? [
        ['html'],
        [
          'junit',
          {
            outputFile: 'results.xml',
          },
        ],
      ]
    : 'html',
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  outputDir: 'test-results/',
  use: {
    actionTimeout: 0,
    baseURL: `http://localhost:${IS_CI && IS_E2E ? 8080 : 8081}`,
    trace: 'on-first-retry',
  },
};

export default config;
