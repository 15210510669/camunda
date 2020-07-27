/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isValid, parseISO} from 'date-fns';
import {format} from 'dates';

export const DATE_FORMAT = 'yyyy-MM-dd';

export function isDateValid(date) {
  const parsedDate = parseISO(date);
  return isValid(parsedDate) && format(parsedDate, DATE_FORMAT) === date;
}
