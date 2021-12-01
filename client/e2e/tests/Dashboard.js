/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as e from './Dashboard.elements.js';
import * as Homepage from './Homepage.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Dashboard').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create a dashboard and reports from a template', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Dashboard'));

  await t.click(e.templateOption('Process performance overview'));

  await t.click(e.templateModalProcessField);
  await t.click(e.option('Invoice Receipt with alternative correlation variable'));

  await t.takeScreenshot('dashboard/dashboardTemplate.png', {fullPage: true});
  await t.resizeWindow(1200, 600);

  await t.click(e.modalConfirmbutton);

  await t.takeScreenshot('dashboard/dashboard-dashboardEditActions.png', {fullPage: true});

  await addAnnotation(
    e.reportTile,
    'Press and hold to\nmove your report\naround the\ndashboard area.'
  );
  await addAnnotation(
    e.reportEditButton,
    'Use the edit button to switch to\nthe Report Edit View',
    {x: 0, y: -50}
  );
  await addAnnotation(
    e.reportDeleteButton,
    'Use the delete button to remove\nthe report from the dashboard.',
    {x: 50, y: 0}
  );
  await addAnnotation(
    e.reportResizeHandle,
    'Use the resize handle to change the\nsize of the report.',
    {x: 50, y: 0}
  );

  await t.takeElementScreenshot(e.body, 'dashboard/dashboard-reportEditActions.png', {
    crop: {right: 750},
  });

  await clearAllAnnotations();

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('Process performance overview');
  await t.expect(e.reportTile.nth(0).textContent).contains('Total Completed Process Instances');
  await t.expect(e.reportTile.nth(2).textContent).contains('Aggregated Process Duration');

  await t.click(e.autoRefreshButton);

  await t.takeScreenshot('dashboard/dashboard-viewMode-monitorFeatures.png', {fullPage: true});
  await t.maximizeWindow();

  await u.gotoOverview(t);

  await t.expect(Homepage.reportItem.visible).ok();
  await t.expect(Homepage.dashboardItem.visible).ok();
});

test('create a report and add it to the Dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await u.save(t);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('invoice');
  await t.expect(e.report.textContent).contains('Start Date');
});

test('renaming a dashboard', async (t) => {
  await u.createNewDashboard(t);
  await t.typeText(e.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('New Name');
});

test('cancel changes', async (t) => {
  await u.createNewDashboard(t);
  await u.save(t);

  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'New Name', {replace: true});
  await u.cancel(t);

  await t.expect(e.dashboardName.textContent).notEql('New Name');
});

// enable this test once https://github.com/DevExpress/testcafe/issues/2863 is fixed
// test('view in fullscreen and dark mode', async t => {
//   await t.click(e.dashboard);
//   await t.click(e.fullscreenButton);

//   await t.expect(e.header.exists).notOk();
//   await t.expect(e.themeButton.visible).ok();

//   await t.click(e.themeButton);

//   await t.expect(e.fullscreenContent.getStyleProperty('background-color')).eql('#222');
// });

test('sharing', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Dashboard'));

  await t.click(e.templateOption('Process performance overview'));

  await t.click(e.templateModalProcessField);
  await t.click(e.option('Invoice Receipt with alternative correlation variable'));
  await t.click(e.modalConfirmbutton);

  await u.save(t);

  await t.expect(e.shareButton.hasAttribute('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  await t.takeScreenshot('dashboard/dashboard-sharingPopover.png', {fullPage: true});

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.reportTile.nth(0).visible).ok();
  await t.expect(e.reportTile.nth(0).textContent).contains('Total Completed Process Instances');
});

test('sharing header parameters', async (t) => {
  await u.createNewDashboard(t);

  await u.save(t);

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl + '&mode=embed');

  await t.expect(e.shareOptimizeIcon.visible).ok();
  await t.expect(e.shareTitle.visible).ok();
  await t.expect(e.shareLink.visible).ok();

  await t.navigateTo(shareUrl + '&mode=embed&header=hidden');

  await t.expect(e.shareHeader.exists).notOk();

  await t.navigateTo(shareUrl + '&header=titleOnly');

  await t.expect(e.shareTitle.exists).ok();
  await t.expect(e.shareLink.exists).notOk();

  await t.navigateTo(shareUrl + '&mode=embed&header=linkOnly');

  await t.expect(e.shareTitle.exists).notOk();
  await t.expect(e.shareLink.exists).ok();
});

test('sharing with filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await t.click(e.addFilterButton);
  await t.click(e.option('Instance State'));

  await u.save(t);

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Suspended'));

  await t.expect(e.shareButton.hasAttribute('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);
  await t.click(e.shareFilterCheckbox);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('No data');
});

test('remove a report from a dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await t.click(e.report);
  await t.click(e.reportDeleteButton);
  await u.save(t);

  await t.expect(e.report.exists).notOk();
});

test('external datasources', async (t) => {
  await u.createNewDashboard(t);

  await t.click(e.addButton);

  await t.takeElementScreenshot(e.reportModal, 'dashboard/dashboard-addAReportModal.png');

  await t.click(e.externalSourceLink);
  await t.typeText(e.externalSourceInput, 'http://example.com/');

  await t.click(e.addReportButton);

  await t.switchToIframe(e.externalReport);

  await t.expect(e.exampleHeading.textContent).contains('Example Domain');
});

test('deleting', async (t) => {
  await u.createNewDashboard(t);

  await u.save(t);

  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.dashboard.exists).notOk();
});

test('filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);

  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await u.save(t);
  await t.click(e.editButton);

  await t.click(e.addFilterButton);
  await t.click(e.option('Instance State'));
  await t.click(e.addFilterButton);
  await t.click(e.option('Start Date'));
  await t.click(e.addFilterButton);
  await t.click(e.option('Variable'));

  await t.typeText(Filter.typeaheadInput, 'invoiceCategory', {replace: true});
  await t.click(Filter.typeaheadOption('invoiceCategory'));
  await t.click(Filter.multiSelectValue('Software License Costs'));
  await t.click(Filter.multiSelectValue('Travel Expenses'));
  await t.click(Filter.multiSelectValue('Misc'));

  await t.click(Filter.customValueCheckbox);

  await t.click(Filter.confirmButton);

  await t.resizeWindow(1200, 550);
  await t.takeElementScreenshot(e.dashboardContainer, 'dashboard/filter-editMode.png', {
    crop: {bottom: 250},
  });

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Running'));

  await u.save(t);

  await t.expect(e.report.visible).ok();
  await t.expect(e.instanceStateFilter.textContent).contains('Running');

  await t.click(e.selectionFilter);
  await t.click(e.switchElement('Software License Costs'));
  await t.click(e.switchElement('Misc'));

  await t.takeElementScreenshot(e.dashboardContainer, 'dashboard/filter-viewMode.png', {
    crop: {bottom: 450},
  });

  await t.click(e.customValueAddButton);
  await t.typeText(e.typeaheadInput, 'Other', {replace: true});
  await t.click(e.typeaheadOption('Other'));

  await t.maximizeWindow();

  await t.expect(e.report.visible).ok();

  await u.gotoOverview(t);
  await t.click(Homepage.dashboardItem);
  await t.expect(e.report.visible).ok();
  await t.expect(e.instanceStateFilter.textContent).contains('Running');

  await t.click(e.editButton);
  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Running'));
  await t.click(e.switchElement('Suspended'));

  await u.save(t);

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('No data');
});
