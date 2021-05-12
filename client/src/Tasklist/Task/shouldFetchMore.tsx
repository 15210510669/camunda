/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function shouldFetchMore(errorCode: string) {
  const ERROR_CODE_PATTERN =
    /task is not assigned|task is not assigned to|task is not active/gi;

  return ERROR_CODE_PATTERN.test(errorCode);
}

export {shouldFetchMore};
