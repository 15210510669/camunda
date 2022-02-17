/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockedModules: {[module: string]: any} = {
  canvas: {
    zoom: jest.fn(),
    resized: jest.fn(),
  },
};

class Manager {
  container: any;
  constructor({container}: any = {}) {
    this.container = container;
  }
  destroy = jest.fn();
  getViews = jest.fn(() => [
    {id: 'invoiceClassification', type: 'decisionTable'},
    {id: 'calc-key-figures', type: 'literalExpression'},
  ]);
  open = jest.fn((view: View) => {
    if (view.type === 'decisionTable') {
      this.container.innerHTML = 'DecisionTable view mock';
    }
    if (view.type === 'literalExpression') {
      this.container.innerHTML = 'LiteralExpression view mock';
    }
  });
  importXML = jest.fn(() => {
    this.container.innerHTML = 'Default View mock';
    return Promise.resolve({});
  });
  getActiveViewer = () => ({
    get: (module: string) => mockedModules[module],
    on: jest.fn(),
    off: jest.fn(),
  });
  getDefinitions = jest.fn(() => {
    return {name: 'Definitions Name Mock'};
  });
}

export default Manager;
