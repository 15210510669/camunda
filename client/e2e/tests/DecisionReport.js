/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './DecisionReport.elements.js';
import * as ProcessReport from './ProcessReport.elements.js';

fixture('Decision Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create a dmn js table report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));

  await t.hover(Homepage.submenuOption('Decision Report'));

  await t.takeElementScreenshot(
    Homepage.createNewMenu,
    'decision/single-report/dmn_report_create.png'
  );

  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.click(Report.configurationButton);
  await t.click(Report.gradientBarsSwitch);

  await t.expect(Report.decisionTable.visible).ok();
  await t.expect(Report.decisionTable.textContent).contains('Hits');
  await t.expect(Report.decisionTableCell(1, 2).textContent).eql('"Misc"');

  await t.typeText(Report.nameEditField, 'Decision Table', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision/single-report/dmn_decision_table.png')
    .maximizeWindow();
});

test('create raw data report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Raw Data');

  await t.expect(Report.reportTable.textContent).contains('Decision Definition Key');
  await t.expect(Report.reportTable.textContent).contains('Input Variables');
  await t.expect(Report.reportTable.textContent).contains('Output Variables');

  await t.typeText(Report.nameEditField, 'DMN - Raw Data Report', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision/single-report/dmn_raw_data_report.png')
    .maximizeWindow();
});

test('save the report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Raw Data');

  await t.typeText(Report.nameEditField, 'new decision report', {replace: true});
  await u.save(t);

  await t.expect(Report.reportTable.visible).ok();

  await u.gotoOverview(t);

  await t.expect(Homepage.reportLabel.textContent).contains('Decision');
});

test('create a single number report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'None');

  await t.expect(Report.reportNumber.visible).ok();

  await t.typeText(Report.nameEditField, 'Progress of Expected Evaluation Count', {replace: true});

  await t.click(ProcessReport.configurationButton);
  await t.click(ProcessReport.goalSwitch);
  await t.typeText(ProcessReport.goalTargetInput, '1000', {replace: true});
  await t.click(ProcessReport.configurationButton);

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision/single-report/dmn_progress_bar.png')
    .maximizeWindow();
});

test('create a report grouped by evaluation date', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Assign Approver Group');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Evaluation Date', 'Automatic');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Report.option('Table'));

  await t.expect(Report.reportTable.visible).ok();

  await u.selectVisualization(t, 'Line Chart');

  await t.typeText(Report.nameEditField, 'Decision Evaluations', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision/single-report/dmn_date_chart.png')
    .maximizeWindow();
});

test('create a report grouped by Input variable', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Input Variable', 'Invoice Amount');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Report.option('Line Chart'));

  await t.expect(Report.reportChart.visible).ok();

  await u.selectGroupby(t, 'Output Variable', 'Classification');
  await u.selectVisualization(t, 'Pie Chart');

  await t.typeText(Report.nameEditField, 'Distribution of Expense Classification', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision/single-report/dmn_pie_chart.png')
    .maximizeWindow();
});

test('filters', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Assign Approver Group');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.click(Report.filterButton);
  await t.hover(Report.filterOption('Output Variable'));

  await t
    .resizeWindow(1400, 700)
    .takeScreenshot('decision/filter/report-with-filterlist-open.png', {fullPage: true})
    .maximizeWindow();
});

async function checkVisualizations(t) {
  await t.expect(Report.option('Number').hasAttribute('disabled')).ok();
  await t.expect(Report.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Line Chart').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Pie Chart').hasAttribute('disabled')).notOk();
}
