/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './ProcessInstance.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {DATE_REGEX} from './constants';
import {processInstancePage as ProcessInstancePage} from './PageModels/ProcessInstance';

fixture('Process Instance')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('Resolve an incident', async (t) => {
  const {
    initialData: {instanceWithIncidentToResolve},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToResolve.processInstanceKey;

  await t.navigateTo(`/processes/${instanceId}`);

  // click and expand incident bar
  await t
    .click(
      screen.queryByRole('button', {
        description: /view 2 incidents in instance/i,
      }),
    )
    .expect(screen.queryByText(/incident type:/i).exists)
    .ok()
    .expect(screen.queryByText(/flow node:/i).exists)
    .ok();

  const withinIncidentsTable = within(screen.queryByTestId('data-table'));
  const withinVariablesList = within(screen.queryByTestId('variables-list'));

  // edit goUp variable
  await t
    .click(withinVariablesList.queryByRole('button', {name: 'Enter edit mode'}))
    .typeText(
      within(ProcessInstancePage.editVariableValueField).queryByRole('textbox'),
      '20',
      {
        paste: true,
        replace: true,
      },
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(withinVariablesList.queryByRole('button', {name: 'Save variable'}))
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk({timeout: 120000});

  // retry one incident to resolve it
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Condition error/i}),
      ).queryByRole('button', {name: 'Retry Incident'}),
    )
    .expect(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Condition error/i}),
      ).queryByTestId('operation-spinner').exists,
    )
    .ok()
    .expect(withinIncidentsTable.queryByTestId('operation-spinner').exists)
    .notOk({timeout: 120000});

  await t
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/is cool\?/i).exists)
    .ok()
    .expect(withinIncidentsTable.queryByText(/where to go\?/i).exists)
    .notOk();

  // add variable isCool

  await t
    .click(ProcessInstancePage.addVariableButton)
    .typeText(
      within(ProcessInstancePage.newVariableNameField).queryByRole('textbox'),
      'isCool',
      {
        paste: true,
      },
    )
    .typeText(
      within(ProcessInstancePage.newVariableValueField).queryByRole('textbox'),
      'true',
      {
        paste: true,
      },
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled'),
    )
    .notOk();

  await t
    .click(screen.queryByRole('button', {name: 'Save variable'}))
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk({timeout: 120000});

  // retry second incident to resolve it
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Extract value error/i}),
      ).queryByRole('button', {name: 'Retry Incident'}),
    )
    .expect(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Extract value error/i}),
      ).queryByTestId('operation-spinner').exists,
    )
    .ok();

  // expect all incidents resolved
  await t
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(screen.queryByTestId('data-table').exists)
    .notOk()
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'COMPLETED-icon',
      ).exists,
    )
    .ok();
});

test('Cancel an instance', async (t) => {
  const {
    initialData: {instanceWithIncidentToCancel},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToCancel.processInstanceKey;

  await t.navigateTo(`/processes/${instanceId}`);

  await t
    .expect(
      screen.queryByRole('button', {
        description: `View 3 Incidents in Instance ${instanceId}`,
      }).exists,
    )
    .ok();

  await t
    .click(
      screen.queryByRole('button', {
        description: `Cancel Instance ${instanceId}`,
      }),
    )
    .click(screen.getByRole('button', {name: 'Apply'}))
    .expect(ProcessInstancePage.operationSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .notOk({timeout: 120000});

  await t
    .expect(
      screen.queryByRole('button', {
        description: `View 3 Incidents in Instance ${instanceId}`,
      }).exists,
    )
    .notOk();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'CANCELED-icon',
      ).exists,
    )
    .ok();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId('end-date')
        .textContent,
    )
    .match(DATE_REGEX);
});
