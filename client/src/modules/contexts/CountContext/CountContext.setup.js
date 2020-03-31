/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const values = {undefined: undefined, null: null, integer: 123};

const filters = {
  integerValues: {
    filterCount: values.integer,
  },
  undefiendValues: {
    filterCount: values.undefined,
  },
  nullValues: {
    filterCount: values.null,
  },
};

export const localStorage = {filters};
export const dataRequests = {
  coreStatistics: {running: 23, active: 13, withIncidents: 123},
  totalCount: 23,
};
