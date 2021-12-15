/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.disclaimer.container;

    return css`
      color: ${colors.color};
      opacity: 0.9;
      font-size: 12px;
      margin-top: 35px;
      width: 489px;
    `;
  }}
`;

export {Container};
