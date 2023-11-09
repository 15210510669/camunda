/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import {login, save, getUser, createNewDashboard, addEditEntityDescription} from '../utils';

import * as Common from './Common.elements.js';
import * as e from './Collection.elements.js';

fixture('Collection')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

async function createCollection(t, name = 'Test Collection') {
  await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
  await t.typeText(Common.modalNameInput, name, {replace: true});

  await t.click(Common.modalConfirmButton);
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);
}

test('create a collection and entities inside it', async (t) => {
  await createCollection(t, 'Test Collection');

  await t.expect(e.collectionTitle.visible).ok();
  await t.expect(e.collectionTitle.textContent).contains('Test Collection');

  await t.click(e.entitiesTab);

  await t.click(Common.createNewButton);

  const createNewMenu = Common.menu('Create New').textContent;

  await t.expect(createNewMenu).notContains('Collection');
  await t.expect(createNewMenu).contains('Dashboard');
  await t.expect(createNewMenu).contains('Report');
  await t.click(Common.createNewButton);

  await createNewDashboard(t);
  const description = 'This is a description of the dashboard.';
  await addEditEntityDescription(t, description);

  await save(t);
  await t.click(e.collectionBreadcrumb);

  await t.expect(Common.dashboardItem.visible).ok();
  await t.expect(Common.dashboardItem.textContent).contains('Blank Dashboard');
  await t.expect(Common.dashboardItem.textContent).contains(description);
});

test('renaming a collection', async (t) => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.editCollectionNameButton);

  await t.typeText(Common.modalNameInput, 'another Collection Name', {replace: true});
  await t.click(Common.modalConfirmButton);

  await t.expect(e.collectionTitle.textContent).contains('another Collection Name');
});

test('copy a collection', async (t) => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.copyCollectionButton);

  await t.typeText(Common.modalNameInput, 'copied collection', {replace: true});

  await t.click(Common.modalConfirmButton);

  await t.expect(e.collectionTitle.textContent).contains('copied collection');
});

test('user permissions', async (t) => {
  await createCollection(t);

  await t.click(e.sourcesTab);

  await t.click(e.addButton);
  const definitionName = 'Invoice Receipt with alternative correlation variable';
  await t.typeText(e.searchField, definitionName, {replace: true});
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);

  await t.click(e.entitiesTab);

  await createNewDashboard(t);
  await save(t);
  await t.click(e.collectionBreadcrumb);

  await t.click(e.userTab);

  await t.click(e.addButton);
  await t.click(Common.usersTypeahead);
  await t.typeText(Common.usersTypeahead, 'sales', {replace: true});
  await t.click(Common.carbonOption('sales'));
  await t.click(Common.modalConfirmButton);

  await t.expect(e.groupItem.visible).ok();
  await t.expect(e.groupItem.textContent).contains('User Group');
  await t.expect(e.groupItem.textContent).contains('Sales');
  await t.expect(e.groupItem.textContent).contains('Viewer');

  await t.click(e.addButton);
  await t.typeText(Common.usersTypeahead, 'mary', {replace: true});
  await t.click(Common.carbonOption('mary'));
  await t.typeText(Common.usersTypeahead, 'peter', {replace: true});
  await t.click(Common.carbonOption('peter')).pressKey('tab');
  await t.click(e.carbonRoleOption('Editor'));
  await t.takeElementScreenshot(Common.modalContainer, 'img/addUser.png');
  await t.click(Common.modalConfirmButton);

  await t
    .resizeWindow(1150, 650)
    .takeElementScreenshot(e.userList, 'img/users.png')
    .maximizeWindow();

  // change permissions
  const managerName = await e.managerName.textContent;
  await t.hover(e.userItem(managerName));
  await t.expect(Common.contextMenu(e.userItem(managerName)).exists).notOk();

  await t.hover(e.groupItem);
  await t.expect(Common.contextMenu(e.groupItem).visible).ok();

  const {username} = getUser(t, 'user2');

  await t.click(e.addButton);
  await t.typeText(Common.usersTypeahead, username, {replace: true});
  await t.click(Common.carbonOption(username));
  await t.click(e.carbonRoleOption('Manager'));
  await t.click(Common.modalConfirmButton);

  await t.hover(e.userItem(managerName));
  await t.expect(Common.contextMenu(e.userItem(managerName)).visible).ok();

  await t.click(Common.contextMenu(e.userItem(managerName)));
  await t.click(Common.edit(e.userItem(managerName)));

  await t.click(e.carbonRoleOption('Viewer'));
  await t.click(Common.modalConfirmButton);

  await t.expect(e.addButton.exists).notOk();

  await t.click(e.entityTab);
  await t.click(Common.dashboardItem);

  await t.expect(Common.editButton.exists).notOk();

  // bulk deleting users
  await t.click(e.usernameDropdown);
  await t.click(e.logoutButton);

  await login(t, 'user2');
  await t.click(e.navItem);

  await t.click(Common.collectionItem);
  await t.click(e.userTab);
  await t.click(Common.selectAllCheckbox.filterVisible());
  await t.click(Common.bulkMenu.filterVisible());
  await t.click(e.remove(Common.bulkMenu.filterVisible()));
  await t.click(Common.modalConfirmButton);
  await t.expect(Common.listItem.filterVisible().count).eql(1);

  // delete collection
  await t.click(e.collectionContextMenu);
  await t.click(e.deleteCollectionButton);
  await t.click(Common.modalConfirmButton);

  await t.expect(Common.collectionItem.exists).notOk();
});

test('add, edit and delete sources', async (t) => {
  await createCollection(t);

  await t.click(e.sourcesTab);

  // add source by definition
  await t.click(e.addButton);
  const definitionName = 'Hiring Demo 5 Tenants';
  await t.typeText(e.searchField, definitionName, {replace: true});
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.visible).ok();
  await t.expect(e.processItem.textContent).contains(definitionName);
  await t.expect(e.processItem.textContent).contains('Process');
  await t.expect(e.processItem.textContent).contains('engineering');

  // add source by tenant
  await t.click(e.addButton);
  const tenantName = 'engineering';
  await t.typeText(Common.comboBox, tenantName, {replace: true});
  await t.click(Common.carbonOption(tenantName));
  await t.click(e.itemCheckbox(3));
  await t.click(e.itemCheckbox(4));
  await t.takeElementScreenshot(Common.modalContainer, 'img/sourceByTenant.png');
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.visible).ok();
  await t.expect(e.decisionItem.visible).ok();
  await t.expect(e.processItem.nth(0).textContent).contains('Book Request with no business key');
  await t.expect(e.decisionItem.textContent).contains('Beverages');

  // edit source
  await t.hover(e.processItem.nth(1));
  await t.expect(Common.contextMenu(e.processItem.nth(1)).visible).ok();
  await t.click(Common.contextMenu(e.processItem.nth(1)));
  await t.click(Common.edit(e.processItem.nth(1)));
  await t.click(e.checkbox('engineering'));
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.nth(1).textContent).notContains('engineering');

  // delete source
  await t.hover(e.decisionItem);
  await t.click(Common.contextMenu(e.decisionItem));
  await t.click(e.remove(e.decisionItem));
  await t.click(Common.modalConfirmButton);
  await t.expect(e.decisionItem.exists).notOk();

  // bulk deleting sources
  await t.click(Common.listItemCheckbox(e.processItem.nth(0)));
  await t.click(Common.listItemCheckbox(e.processItem.nth(1)));
  await t.click(Common.bulkMenu.filterVisible());
  await t.click(e.remove(Common.bulkMenu.filterVisible()));
  await t.click(Common.modalConfirmButton);
  await t.expect(Common.listItem.filterVisible().exists).notOk();
});
