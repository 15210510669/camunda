/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as e from './Dashboard.elements.js';
import * as Common from './Common.elements.js';
import * as Filter from './Filter.elements.js';
import * as Alert from './Alerts.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Collection from './Collection.elements.js';

fixture('Dashboard')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await u.login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

test('create a dashboard and reports from a template', async (t) => {
  await t.click(Common.createNewMenu).click(Common.option('Collection'));
  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);

  await t.resizeWindow(1300, 750);
  await t.click(Common.createNewMenu);
  await t.click(Common.option('Dashboard'));

  await t.click(Common.templateModalProcessField);
  await t.click(Common.carbonOption('Invoice Receipt with alternative correlation variable'));
  await t.click(e.templateOption('Process performance overview'));

  await t.takeScreenshot('img/dashboardTemplate.png', {fullPage: true});
  await t.resizeWindow(1200, 600);

  await t.click(Common.modalConfirmButton);

  await u.addEditEntityDescription(t, 'This is a description of the dashboard.');

  await t.takeScreenshot('img/dashboard-dashboardEditActions.png', {fullPage: true});

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

  await t.takeElementScreenshot(e.body, 'img/dashboard-reportEditActions.png', {
    crop: {right: 750},
  });

  await clearAllAnnotations();

  await t.click(e.autoRefreshButton);
  await t.click(Common.option('1 minute'));

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('Process performance overview');
  await t.expect(e.reportTile.nth(0).textContent).contains('Throughput (30-day rolling)');
  await t.expect(e.reportTile.nth(2).textContent).contains('99th Percentile Duration');

  await t.click(e.autoRefreshButton);

  await t.takeScreenshot('img/dashboard-viewMode-monitorFeatures.png', {fullPage: true});
  await t.maximizeWindow();

  await t.click(e.collectionLink);

  await t.expect(Common.reportItem.visible).ok();
  await t.expect(Common.dashboardItem.visible).ok();
});

test('create a report and add it to the Dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await u.save(t);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(Report.reportRenderer.textContent).contains('invoice');
  await t.expect(Report.reportRenderer.textContent).contains('Start Date');
});

test('renaming a dashboard', async (t) => {
  await u.createNewDashboard(t);
  await t.typeText(Common.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('New Name');
});

test('cancel changes', async (t) => {
  await u.createNewDashboard(t);
  await u.save(t);

  await t.click(Common.editButton);
  await t.typeText(Common.nameEditField, 'New Name', {replace: true});
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
  await t.click(Common.createNewMenu);
  await t.click(Common.option('Dashboard'));

  await t.click(e.templateOption('Process performance overview'));

  await t.click(Common.templateModalProcessField);
  await t.click(Common.carbonOption('Invoice Receipt with alternative correlation variable'));
  await t.click(Common.modalConfirmButton);

  await u.save(t);

  await t.expect(Common.shareButton.hasAttribute('disabled')).notOk();

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);

  await t.takeScreenshot('img/dashboard-sharingPopover.png', {fullPage: true});

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.reportTile.nth(0).visible).ok();
  await t.expect(e.reportTile.nth(0).textContent).contains('Throughput (30-day rolling)');
});

test('sharing header parameters', async (t) => {
  await u.createNewDashboard(t);

  await u.save(t);

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl + '&mode=embed');

  await t.expect(Common.shareOptimizeIcon.visible).ok();
  await t.expect(Common.shareTitle.visible).ok();
  await t.expect(Common.shareLink.visible).ok();

  await t.navigateTo(shareUrl + '&mode=embed&header=hidden');

  await t.expect(Common.shareHeader.exists).notOk();

  await t.navigateTo(shareUrl + '&header=titleOnly');

  await t.expect(Common.shareTitle.exists).ok();
  await t.expect(Common.shareLink.exists).notOk();

  await t.navigateTo(shareUrl + '&mode=embed&header=linkOnly');

  await t.expect(Common.shareTitle.exists).notOk();
  await t.expect(Common.shareLink.exists).ok();
});

test('sharing with filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await t.click(e.addFilterButton);
  await t.click(Common.option('Instance State'));

  await u.save(t);

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Suspended'));

  await t.expect(Common.shareButton.hasAttribute('disabled')).notOk();

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);
  await t.click(e.shareFilterCheckbox);

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(Report.reportRenderer.textContent).contains('No data');
});

test('remove a report from a dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await t.click(e.reportDeleteButton);
  await u.save(t);

  await t.expect(Report.reportRenderer.exists).notOk();
});

test('external datasources', async (t) => {
  await u.createNewDashboard(t);

  await t.click(Common.addButton);

  await t.takeElementScreenshot(Common.modalContainer, 'img/dashboard-addAReportModal.png');

  await t.click(e.externalSourceLink);
  await t.typeText(e.externalSourceInput, 'http://example.com/');

  await t.takeElementScreenshot(
    Common.modalContainer,
    'img/dashboard-addAReportModal-externalReport.png'
  );

  await t.click(e.addTileButton);

  await t.switchToIframe(e.externalReport);

  await t.expect(e.exampleHeading.textContent).contains('Example Domain');
});

test('text report', async (t) => {
  await u.createNewDashboard(t);

  await t.click(Common.addButton);
  await t.click(e.textReportLink);
  await t.typeText(e.textReportInput, 'This is a text report ');

  await t.click(e.textReportToolButton('Bold'));
  await t.typeText(e.textReportInput, 'with Bold text ');
  await t.click(e.textReportToolButton('Bold'));

  await t.click(e.textReportToolButton('Italic'));
  await t.typeText(e.textReportInput, 'with Italic text');
  await t.click(e.textReportToolButton('Italic'));

  await t.click(e.textReportInsertDropdown);
  await t.click(Common.option('Link'));
  await t.typeText(e.textReportUrlInput, 'https://example.com/');
  await t.typeText(e.textReportAltInput, 'This is a link to example.com');
  await t.click(e.textReportInsertAddButton);

  await t.click(e.textReportInsertDropdown);
  await t.click(Common.option('Image'));
  await t.typeText(e.textReportUrlInput, 'https://avatars3.githubusercontent.com/u/2443838');
  await t.typeText(e.textReportAltInput, 'This is a camunda logo');
  await t.click(e.textReportInsertAddButton);

  await t.takeElementScreenshot(
    Common.modalContainer,
    'img/dashboard-addAReportModal-textReport.png'
  );

  await t.click(e.addTileButton);
  await t.click('.DashboardRenderer');

  await t.expect(e.textReportField('strong').visible).ok();
  await t.expect(e.textReportField('em').visible).ok();
});

test('deleting', async (t) => {
  await u.createNewDashboard(t);

  await u.save(t);

  await t.click(Common.deleteButton);
  await t.click(Common.modalConfirmButton);

  await t.expect(e.dashboard.exists).notOk();
});

test('filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);

  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Blank report');

  await u.save(t);
  await t.click(Common.editButton);

  await t.click(e.addFilterButton);
  await t.click(Common.option('Instance State'));
  await t.click(e.addFilterButton);
  await t.click(Common.option('Start Date'));
  await t.click(e.addFilterButton);
  await t.click(Common.option('Variable'));

  await t.typeText(Filter.typeaheadInput, 'invoiceCategory', {replace: true});
  await t.click(Common.typeaheadOption('invoiceCategory'));
  await t.click(Filter.multiSelectValue('Software License Costs'));
  await t.click(Filter.multiSelectValue('Travel Expenses'));
  await t.click(Filter.multiSelectValue('Misc'));

  await t.click(Filter.customValueCheckbox);

  await t.click(Common.modalConfirmButton);

  await t.resizeWindow(1200, 550);
  await t.takeElementScreenshot(e.dashboardContainer, 'img/filter-editMode.png', {
    crop: {bottom: 250},
  });

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Running'));

  await u.save(t);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(e.instanceStateFilter.textContent).contains('Running');

  await t.click(e.selectionFilter);
  await t.click(e.switchElement('Software License Costs'));
  await t.click(e.switchElement('Misc'));

  await t.takeElementScreenshot(e.dashboardContainer, 'img/filter-viewMode.png', {
    crop: {bottom: 450},
  });

  await t.click(e.customValueAddButton);
  await t.typeText(e.typeaheadInput, 'Other', {replace: true});
  await t.click(Common.typeaheadOption('Other'));

  await t.maximizeWindow();

  await t.expect(Report.reportRenderer.visible).ok();

  await u.gotoOverview(t);
  await t.click(Common.dashboardItem);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(e.instanceStateFilter.textContent).contains('Running');

  await t.click(Common.editButton);
  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Running'));
  await t.click(e.switchElement('Suspended'));

  await u.save(t);

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(Report.reportRenderer.textContent).contains('No data');
});

test('version selection', async (t) => {
  await t.click(Common.createNewMenu).click(Common.option('Collection'));
  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);

  await u.createNewReport(t);
  await t.typeText(Common.nameEditField, 'Number Report', {replace: true});
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.save(t);

  await t.click(e.collectionLink);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'Number Report');
  await u.save(t);

  // Creating
  await t.click(e.alertsDropdown);
  await t.click(Common.option('New Alert'));
  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Test alert', {replace: true});
  await t.click(Common.typeahead);
  await t.click(Common.typeaheadOption('Number Report'));
  await t.typeText(Alert.inputWithLabel('Send Email to'), 'test@email.com ');
  await t.click(Common.modalConfirmButton);
  await t.click(Common.notificationCloseButton);

  // editing
  await t.click(e.alertsDropdown);
  await t.click(Common.option('Test alert'));
  await t.typeText(Alert.inputWithLabel('Alert Name'), 'another alert name', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.click(Common.notificationCloseButton);

  // deleting
  await t.click(e.alertsDropdown);
  await t.click(Common.option('another alert name'));
  await t.click(e.alertDeleteButton);
  await t.click(Common.modalConfirmButton);
  await t.click(Common.notificationCloseButton);
  await t.click(e.alertsDropdown);
  await t.expect(Common.option('Test alert').exists).notOk();
});

test('add a report from the dashboard', async (t) => {
  await u.createNewDashboard(t);

  await t
    .click(Common.addButton)
    .click(e.reportModalOptionsButton)
    .click(e.reportModalDropdownOption.withText('New Report from a template'))
    .click(e.addTileButton);

  await t
    .click(Common.templateModalProcessField)
    .click(Common.carbonOption('Invoice Receipt with alternative correlation variable'))
    .click(e.blankReportButton)
    .click(Common.modalConfirmButton)
    .click('.DashboardRenderer');

  await t
    .click(Common.addButton)
    .click(e.reportModalOptionsButton)
    .click(e.reportModalDropdownOption.withText('New Report from a template'))
    .click(e.addTileButton);

  await t.click(Common.templateModalProcessField);

  await t
    .expect(Common.selectedOption('Invoice Receipt with alternative correlation variable').exists)
    .ok();

  await t.click(Common.modalConfirmButton).click('.DashboardRenderer');

  await u.save(t);

  await t.expect(e.reportTile.nth(0).textContent).contains('Blank report');
  await t.expect(e.reportTile.nth(1).textContent).contains('Locate bottlenecks on a Heatmap');
});

test('add, edit and remove dashboards description', async (t) => {
  await u.createNewDashboard(t);

  await t.resizeWindow(1200, 600);

  // Add description
  await t.expect(Common.descriptionParagraph.exists).notOk();
  await t.expect(Common.addDescriptionButton.textContent).contains('Add Description');
  const description = 'This is a description of the dashboard.';
  await u.addEditEntityDescription(t, description, 'img/dashboad-descriptionModal.png');

  await t.expect(Common.descriptionField.textContent).contains(description);

  await u.save(t);
  await u.gotoOverview(t);
  await t.expect(Common.dashboardItem.textContent).contains(description);

  await t.click(Common.dashboardItem);
  await t.expect(Common.descriptionField.textContent).contains(description);

  // Edit description
  await t.click(Common.editButton);

  await t.expect(Common.addDescriptionButton.textContent).contains('Edit');

  const newDescription =
    'This is a new description of the dashboard. This time the description is very long and will not fit in one line. It will display ellipsis and More button.';
  await u.addEditEntityDescription(t, newDescription);

  await t.expect(Common.descriptionField.textContent).contains(newDescription);

  await u.save(t);

  // Toggle show less/more
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  await t.click(Common.showLessMoreDescriptionButton);

  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).notOk();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('Less');

  await t.takeElementScreenshot(e.dashboardContainer, 'img/dashboard-showMoreDescription.png', {
    crop: {bottom: 200},
  });

  await t.click(Common.showLessMoreDescriptionButton);
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  // Remove description
  await t.click(Common.editButton);
  await u.addEditEntityDescription(t);
  await t.expect(Common.descriptionParagraph.exists).notOk();
  await t.expect(Common.addDescriptionButton.textContent).contains('Add Description');

  await u.save(t);

  await t.expect(Common.descriptionField.exists).notOk();
});

test('copy instant preview dashboard', async (t) => {
  // Create a copy of the instant preview dashboard
  await t.click(e.dashboardsLink);
  await t.click(Common.processItem);

  await t.click(e.createCopyButton);
  await t.click(Common.modalConfirmButton);
  await u.save(t);

  await u.gotoOverview(t);

  await t.expect(Common.collectionItem.textContent).contains('AnalysisTestingProcess');

  await t.click(Common.collectionItem);
  await t.expect(Common.dashboardItem.count).eql(1);
  await t.expect(Common.dashboardItem.textContent).contains('Instant Preview Dashboard');

  // Create another copy to check if only one collection is created
  await t.click(e.dashboardsLink);
  await t.click(Common.processItem);

  await t.click(e.createCopyButton);
  await t.click(Common.modalConfirmButton);
  await t.typeText(Common.nameEditField, 'New Name', {replace: true});
  await u.save(t);

  await u.gotoOverview(t);
  await t.click(Common.collectionItem);
  await t.expect(Common.dashboardItem.count).eql(2);
  await t.expect(Common.dashboardItem.nth(0).textContent).contains('New Name');
  await t.expect(Common.dashboardItem.nth(1).textContent).contains('Instant Preview Dashboard');

  // Create a new collection if the first one was renamed
  await t.click(Collection.collectionContextMenu);
  await t.click(Collection.editCollectionNameButton);

  await t.typeText(Common.modalNameInput, 'another Collection Name', {replace: true});
  await t.click(Common.modalConfirmButton);

  await u.gotoOverview(t);
  await t.expect(Common.collectionItem.count).eql(1);
  await t.expect(Common.collectionItem.textContent).contains('another Collection Name');

  await t.click(e.dashboardsLink);
  await t.click(Common.processItem);

  await t.click(e.createCopyButton);
  await t.click(Common.modalConfirmButton);
  await u.save(t);

  await u.gotoOverview(t);

  await t.expect(Common.collectionItem.count).eql(2);
  await t.expect(Common.collectionItem.nth(0).textContent).contains('AnalysisTestingProcess');
  await t.expect(Common.collectionItem.nth(1).textContent).contains('another Collection Name');
});
