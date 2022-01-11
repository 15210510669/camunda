/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {t} from 'testcafe';
import {screen, within} from '@testing-library/testcafe';

type OptionalFilter =
  | 'Variable'
  | 'Instance Id(s)'
  | 'Parent Instance Id'
  | 'Operation Id'
  | 'Error Message'
  | 'Start Date'
  | 'End Date';

const displayOptionalFilter = async (filterName: OptionalFilter) => {
  await t
    .click(screen.queryByTestId('more-filters-dropdown').shadowRoot().child())
    .click(
      within(
        screen.getByTestId('more-filters-dropdown').shadowRoot()
      ).getByText(filterName)
    );
};

export {displayOptionalFilter};
