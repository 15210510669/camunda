/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

export class MigrationView {
  private page: Page;

  readonly targetProcessDropdown: Locator;
  readonly targetVersionDropdown: Locator;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly summaryNotification: Locator;

  constructor(page: Page) {
    this.page = page;

    this.targetProcessDropdown = page.getByRole('combobox', {
      name: 'Target Process',
      exact: true,
    });

    this.targetVersionDropdown = page.getByRole('combobox', {
      name: 'Target Version',
    });

    this.nextButton = page.getByRole('button', {
      name: /^next$/i,
    });

    this.confirmButton = page.getByRole('button', {
      name: /^confirm$/i,
    });

    this.summaryNotification = page.getByRole('main').getByRole('status');
  }

  async selectTargetProcess(option: string) {
    await this.targetProcessDropdown.click();
    await this.page.getByRole('option', {name: option}).click();
  }

  async selectTargetVersion(option: string) {
    await this.targetVersionDropdown.click();
    await this.page.getByRole('option', {name: option}).click();
  }

  mapFlowNode({
    sourceFlowNodeName,
    targetFlowNodeName,
  }: {
    sourceFlowNodeName: string;
    targetFlowNodeName: string;
  }) {
    return this.page
      .getByLabel(`Target flow node for ${sourceFlowNodeName}`)
      .selectOption(targetFlowNodeName);
  }

  selectTargetSourceFlowNode(flowNodeName: string) {
    return this.page
      .getByRole('cell', {name: flowNodeName, exact: true})
      .click();
  }
}
