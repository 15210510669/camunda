/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';

export async function login(username: string, password: string): Promise<string> {
  const response = await post('api/authentication', {username, password});

  return await response.text();
}
