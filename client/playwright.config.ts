/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {devices, PlaywrightTestConfig} from '@playwright/test';

const IS_CI = Boolean(process.env.CI);

/**
 * See https://playwright.dev/docs/test-configuration.
 */
const config: PlaywrightTestConfig = {
  testDir: './e2e-playwright',
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },
  fullyParallel: true,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI ? 1 : undefined,
  reporter: 'html',
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  outputDir: 'test-results/',
  webServer: IS_CI
    ? undefined
    : {
        command: 'yarn start:e2e',
        port: 3001,
      },
  use: {
    actionTimeout: 0,
    baseURL: IS_CI ? 'http://localhost:8080' : 'http://localhost:3001',
    trace: 'on-first-retry',
  },
};

export default config;
