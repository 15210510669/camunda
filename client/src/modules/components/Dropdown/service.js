/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function findLetterOption(options, letter, startIndex) {
  const found = findOptionFromIndex(options, letter, startIndex);
  if (found) {
    return found;
  } else {
    if (startIndex > 0) {
      return findOptionFromIndex(options, letter, 0);
    }
  }
}

function findOptionFromIndex(options, letter, startIndex) {
  return options
    .slice(startIndex)
    .find(el => el.textContent[0].toLowerCase() === letter.toLowerCase());
}
