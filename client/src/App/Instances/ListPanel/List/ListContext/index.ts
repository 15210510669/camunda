/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Instance} from 'modules/types';
import React, {useContext} from 'react';

type ContextProps = {
  data: Array<Instance>;
  onSort: (key: string) => void;
  rowsToDisplay: number;
  isDataLoaded: boolean;
  handleOperation: (instanceId: string) => void;
  handleOperationFailure: (instanceId: string) => void;
};

// @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
const ListContext = React.createContext<ContextProps>();
export const useListContext = () => useContext(ListContext);

export default ListContext;
