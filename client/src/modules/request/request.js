/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

let responseInterceptor = null;

export async function request({url, method, body, query, headers}) {
  const resourceUrl = query ? `${url}?${stringifyQuery(query)}` : `${url}`;

  let response = await fetch(resourceUrl, {
    method,
    credentials: 'include',
    body: typeof body === 'string' ? body : JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
    mode: 'cors'
  });

  if (typeof responseInterceptor === 'function') {
    await responseInterceptor(response);
  }

  if (response.status >= 200 && response.status < 300) {
    return response;
  } else {
    throw response;
  }
}

export function stringifyQuery(query) {
  return Object.keys(query).reduce((queryStr, key) => {
    const value = query[key];

    if (queryStr === '') {
      return `${key}=${encodeURIComponent(value)}`;
    }

    return `${queryStr}&${key}=${encodeURIComponent(value)}`;
  }, '');
}

export function setResponseInterceptor(fct) {
  responseInterceptor = fct;
}
