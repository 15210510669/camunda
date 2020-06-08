/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

interface TRProps {
  hasNoBorder?: boolean;
}

const Table = styled.table`
  width: 100%;
  font-size: 14px;
  border-collapse: collapse;
`;

const RowTH = styled.th`
  width: 250px;
  padding: 12px 20px;
  text-align: left;
  color: ${({theme}) => theme.colors.text.button};
`;

const ColumnTH = styled.th`
  font-weight: normal;
  font-style: italic;
  color: ${({theme}) => theme.colors.label01};
  text-align: left;
  padding: 12px;

  &:first-child {
    padding-left: 20px;
  }
`;

const TD = styled.td`
  padding: 12px;
  color: ${({theme}) => theme.colors.ui06};
`;

const TR = styled.tr<TRProps>`
  ${({hasNoBorder, theme}) =>
    !hasNoBorder && `border-bottom: 1px solid ${theme.colors.ui05}`}
`;

export {Table, RowTH, ColumnTH, TR, TD};
