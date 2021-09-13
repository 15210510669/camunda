/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const Chart = jest.fn(() => {
  return {
    destroy: jest.fn(),
  };
});

Chart.defaults = {
  font: {
    family: '',
  },
  set: jest.fn(),
};

Chart.register = jest.fn();
const registerables = [];

class LineController {
  constructor(...args) {}
}

export {Chart, registerables, LineController};
