/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

export const Body = themed(styled.div`
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  border-top: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
`);
