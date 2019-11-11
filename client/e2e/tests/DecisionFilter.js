/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ensureLicense, cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './DecisionReport.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Decision Report Filter')
  .page(config.endpoint)
  .before(ensureLicense)
  .after(cleanEntities)
  .beforeEach(u.login);

test('should apply a filter to the report result', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'None');

  const unfiltered = +(await Report.reportRenderer.textContent);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Input Variable'));
  await t.click(Filter.variableFilterTypeahead);
  await t.click(Filter.variableFilterTypeaheadOption('Invoice Amount'));
  await t.click(Filter.variableFilterOperatorButton('is less than'));

  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.click(Report.primaryModalButton);

  const filtered = +(await Report.reportRenderer.textContent);

  await t.expect(unfiltered).gt(filtered);
});

test('should have seperate input and output variables', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'None');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Input Variable'));
  await t.click(Filter.variableFilterTypeahead);
  await t.expect(Filter.variableFilterTypeahead.textContent).notContains('Classification');
  await t.click(Filter.modalCancel);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Output Variable'));
  await t.click(Filter.variableFilterTypeahead);

  await t.expect(Filter.variableFilterTypeahead.textContent).notContains('Invoice Amount');
  await t.expect(Filter.variableFilterTypeahead.textContent).contains('Classification');
});
