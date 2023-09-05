/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  instanceWithIncident,
  mockResponses,
  runningInstance,
} from './processInstance.mocks';

test.describe('modifications', () => {
  for (const theme of ['light', 'dark']) {
    test(`with helper modal - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /modify instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`with add variable state - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await processInstancePage.addVariableButton.click();

      await expect(page).toHaveScreenshot();
    });

    test(`diagram badges and flow node instance history panel - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: instanceWithIncident.detail,
          flowNodeInstances: instanceWithIncident.flowNodeInstances,
          statistics: instanceWithIncident.statistics,
          sequenceFlows: instanceWithIncident.sequenceFlows,
          variables: instanceWithIncident.variables,
          xml: instanceWithIncident.xml,
          incidents: instanceWithIncident.incidents,
          metaData: instanceWithIncident.metaData,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /reset diagram zoom/i,
        })
        .click();

      await processInstancePage.diagram.getByText(/check payment/i).click();

      await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

      await page
        .getByTitle(
          /move selected instance in this flow node to another target/i,
        )
        .click();

      await processInstancePage.diagram.getByText(/check order items/i).click();
      await processInstancePage.diagram.getByText(/check payment/i).click();
      await page
        .getByRole('button', {
          name: /add single flow node instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`apply modifications summary modal - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: instanceWithIncident.detail,
          flowNodeInstances: instanceWithIncident.flowNodeInstances,
          statistics: instanceWithIncident.statistics,
          sequenceFlows: instanceWithIncident.sequenceFlows,
          variables: instanceWithIncident.variables,
          xml: instanceWithIncident.xml,
          incidents: instanceWithIncident.incidents,
          metaData: instanceWithIncident.metaData,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await processInstancePage.diagram.getByText(/check payment/i).click();

      await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

      await page
        .getByTitle(
          /move selected instance in this flow node to another target/i,
        )
        .click();

      await processInstancePage.diagram.getByText(/check order items/i).click();

      const firstVariableValueInput = page
        .getByRole('textbox', {
          name: /value/i,
        })
        .nth(0);

      await firstVariableValueInput.clear();
      await firstVariableValueInput.fill('"test"');
      await page.keyboard.press('Tab');

      await page
        .getByRole('button', {
          name: /apply modifications/i,
        })
        .click();

      await expect(
        page.getByText(/planned modifications for process instance/i),
      ).toBeVisible();

      await expect(
        page.getByRole('button', {
          name: /delete flow node modification/i,
        }),
      ).toBeVisible();

      await expect(
        page.getByRole('button', {
          name: /delete variable modification/i,
        }),
      ).toBeVisible();

      await expect(page).toHaveScreenshot();
    });
  }
});
