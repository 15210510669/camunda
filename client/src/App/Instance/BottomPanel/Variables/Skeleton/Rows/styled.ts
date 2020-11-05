/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import BasicMultiRow from 'modules/components/MultiRow';
import {BaseBlock} from 'modules/components/Skeleton';

const MultiRow = styled(BasicMultiRow)`
  width: 100%;
  overflow: hidden;
`;

const Row = styled.div`
  ${({theme}) => {
    const colors = theme.colors.variables.skeleton.row;

    return css`
      display: flex;
      border-top: 1px solid ${colors.borderColor};
      padding: 13px 10px;
    `;
  }}
`;

const VariableBlock = styled(BaseBlock)`
  margin-left: 4px;
  height: 14px;
  max-width: 200px;
  flex-grow: 1;
`;

const ValueBlock = styled(BaseBlock)`
  margin-left: 30px;
  margin-right: 40px;
  height: 14px;
  flex-grow: 2;
`;

export {MultiRow, Row, VariableBlock, ValueBlock};
