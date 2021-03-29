/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getCurrentCopyrightNoticeText = () => {
  return `© Camunda Services GmbH ${new Date().getFullYear()}. All rights reserved. | ${
    process.env.REACT_APP_VERSION
  }`;
};

export {getCurrentCopyrightNoticeText};
