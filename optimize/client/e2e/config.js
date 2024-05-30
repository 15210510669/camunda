/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const users = require('../demo-data/users.json');

const config = {
  endpoint: 'http://localhost:3000',
  collectionsEndpoint: 'http://localhost:3000/#/collections',
  elasticSearchEndpoint: 'http://localhost:9200',
  keycloak: {
    endpoint: 'http://localhost:18080/auth',
    username: 'admin',
    password: 'admin',
    client_id: 'admin-cli',
  },
};
if (process.env.CONTEXT === 'sm') {
  const browser1 = createMapOfUsers(users.slice(0, 2));
  const browser2 = createMapOfUsers(users.slice(2, 4));
  const borwser3 = createMapOfUsers(users.slice(4, 6));
  const browsers = [browser1, browser2, borwser3];

  config.users = {
    Chrome: browsers,
    headless: browsers,
    Firefox: browsers,
    'Microsoft Edge': browsers,
  };
} else {
  config.users = {
    Chrome: [
      createMapOfUsers(users.slice(0, 2)),
      createMapOfUsers(users.slice(2, 4)),
      createMapOfUsers(users.slice(4, 6)),
    ],
    headless: [
      createMapOfUsers(users.slice(6, 8)),
      createMapOfUsers(users.slice(8, 10)),
      createMapOfUsers(users.slice(10, 12)),
    ],
    Firefox: [
      createMapOfUsers(users.slice(12, 14)),
      createMapOfUsers(users.slice(14, 16)),
      createMapOfUsers(users.slice(16, 18)),
    ],
    'Microsoft Edge': [
      createMapOfUsers(users.slice(18, 20)),
      createMapOfUsers(users.slice(20, 22)),
      createMapOfUsers(users.slice(22, 24)),
    ],
  };
}

export default config;

function createMapOfUsers(users) {
  return users.reduce((map, user, idx) => {
    map['user' + (idx + 1)] = {username: user, password: user};
    return map;
  }, {});
}
