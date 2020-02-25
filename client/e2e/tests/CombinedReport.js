/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Combined from './CombinedReport.elements.js';

fixture('Combined Report')
  .page(config.endpoint)
  .beforeEach(u.login)
  .afterEach(cleanEntities);

async function createReport(
  t,
  name,
  definition = 'Lead Qualification',
  visualization = 'Line Chart',
  completed
) {
  await u.createNewReport(t);
  await u.selectDefinition(t, definition, 'All');

  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectVisualization(t, visualization);

  if (completed) {
    await t.click(Report.filterButton);
    await t.click(Report.filterOption('Completed Instances Only'));
  }

  await t.typeText(Report.nameEditField, name, {replace: true});

  await u.save(t);

  await u.gotoOverview(t);
}

test('combine two single number reports', async t => {
  await createReport(t, 'Leads');
  await createReport(t, 'Monthly Sales');
  await createReport(t, 'Invoice Count');
  await createReport(t, 'Incoming leads per day');
  await createReport(t, 'Weekly rejection Rate');
  await createReport(t, 'Weekly Sales');
  await createReport(t, 'Invoice Average');

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));

  await t.hover(Homepage.submenuOption('Combined Process Report'));

  await t.takeElementScreenshot(
    Homepage.createNewMenu,
    'process/combined-report/combined-report-create.png'
  );

  await t.click(Homepage.submenuOption('Combined Process Report'));
  await t.typeText(Report.nameEditField, 'Combined Report', {replace: true});

  await t
    .resizeWindow(1150, 700)
    .takeScreenshot('process/combined-report/combined-report.png', {fullPage: true})
    .maximizeWindow();

  await t.click(Combined.singleReport('Leads'));
  await t.click(Combined.singleReport('Invoice Average'));

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.save(t);

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.gotoOverview(t);

  await t.expect(Homepage.reportLabel.textContent).contains('Combined');
});

test('combine two single table reports and reorder them', async t => {
  await createReport(t, 'Another Table Report', 'Lead Qualification', 'Table');
  await createReport(t, 'Table Report', 'Lead Qualification', 'Table', true);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Table Report'));
  await t.click(Combined.singleReport('Another Table Report'));

  await t.expect(Combined.reportTable.visible).ok();

  await t.typeText(Report.nameEditField, 'Combined Table Report', {replace: true});

  await t
    .resizeWindow(1150, 700)
    .takeScreenshot('process/combined-report/table-report.png', {fullPage: true})
    .maximizeWindow();

  await t.dragToElement(Combined.singleReport('Table Report'), Combined.dragEndIndicator);

  await t.expect(Combined.reportTable.visible).ok();
});

test('combine two single chart reports and change their colors', async t => {
  await createReport(t, 'Line Report - 1', 'Lead Qualification', 'Line Chart');
  await createReport(t, 'Line Report - 2', 'Lead Qualification', 'Line Chart', true);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Line Report - 1'));
  await t.click(Combined.singleReport('Line Report - 2'));

  await t.expect(Combined.reportChart.visible).ok();

  await t.typeText(Report.nameEditField, 'Combined Chart Report', {replace: true});

  await t.resizeWindow(1150, 700);

  await t.click(Combined.reportColorPopover('Line Report - 2'));

  await t
    .takeScreenshot('process/combined-report/area-chart-report.png', {fullPage: true})
    .maximizeWindow();

  await t.click(Combined.redColor);

  await t.expect(Combined.reportChart.visible).ok();
});

test('open the configuration popover and add a goal line', async t => {
  await createReport(t, 'Bar Report - 1', 'Lead Qualification', 'Bar Chart');
  await createReport(t, 'Bar Report - 2', 'Lead Qualification', 'Bar Chart', true);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Bar Report - 1'));
  await t.click(Combined.singleReport('Bar Report - 2'));

  await t.typeText(Report.nameEditField, 'Combined Chart Report', {replace: true});

  await t.resizeWindow(1150, 700);

  await t.click(Combined.configurationButton);
  await t.click(Combined.goalSwitch);
  await t.typeText(Combined.goalInput, '300', {replace: true});

  await t
    .takeScreenshot('process/combined-report/combined-config.png', {fullPage: true})
    .maximizeWindow();

  await t.click(Combined.configurationButton);

  await t.expect(Combined.reportChart.visible).ok();
});
