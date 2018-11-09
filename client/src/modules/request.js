const handlers = [];

export function put(url, body, options = {}) {
  return request({
    url,
    body,
    method: 'PUT',
    ...options
  });
}

export function post(url, body, options = {}) {
  return request({
    url,
    body,
    method: 'POST',
    ...options
  });
}

export function get(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'GET',
    ...options
  });
}

export function del(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'DELETE',
    ...options
  });
}

export function addHandler(fct) {
  handlers.push(fct);
}

export function removeHandler(fct) {
  handlers.splice(handlers.indexOf(fct), 1);
}

export async function request({url, method, body, query, headers}) {
  const resourceUrl = query ? `${url}?${formatQuery(query)}` : url;

  let response = await fetch(resourceUrl, {
    method,
    body: processBody(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
    mode: 'cors',
    credentials: 'same-origin'
  });

  handlers.forEach(async fct => {
    response = await fct(response);
  });

  if (response.status >= 200 && response.status < 300) {
    return response;
  } else {
    throw response;
  }
}

export function formatQuery(query) {
  return Object.keys(query).reduce((queryStr, key) => {
    const value = query[key];

    if (queryStr === '') {
      return `${key}=${encodeURIComponent(value)}`;
    }

    return `${queryStr}&${key}=${encodeURIComponent(value)}`;
  }, '');
}

function processBody(body) {
  if (typeof body === 'string') {
    return body;
  }

  return JSON.stringify(body);
}
