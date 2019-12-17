/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import Table from 'modules/components/Table';

import SplitPane from 'modules/components/SplitPane';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';

export const Spinner = styled(SpinnerSkeleton)`
  margin-top: 39px;
`;

export const PaneBody = styled(SplitPane.Pane.Body)`
  border-top: none;
`;

export const EmptyTR = styled(Table.TR)`
  border: 0;
  padding: 0;
`;
