/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format, parseISO} from 'date-fns';

function parseDate(dateString: string | Date) {
  return typeof dateString === 'string' ? parseISO(dateString) : dateString;
}

export function formatDate(
  dateString: string | Date | null,
  placeholder: string | null = '--'
) {
  return dateString
    ? format(parseDate(dateString), 'yyyy-MM-dd HH:mm:ss')
    : placeholder;
}
