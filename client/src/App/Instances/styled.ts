/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {default as SplitPaneComponent} from 'modules/components/SplitPane';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

import {HEADER_HEIGHT} from './../Header/styled';

const Instances = styled.main`
  height: calc(100vh - ${HEADER_HEIGHT}px);
  position: relative;
  overflow: hidden;
`;

const Content = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: calc(100% - ${COLLAPSABLE_PANEL_MIN_WIDTH});
`;

const SplitPane = styled(SplitPaneComponent)`
  border-radius: 3px 0 0 0;
  width: 100%;
`;

const Pane = styled(SplitPane.Pane)`
  border-radius: inherit;
`;

const PaneHeader = styled(SplitPane.Pane.Header)`
  border-radius: inherit;
`;

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel;

    return css`
      width: 1px;
      background-color: ${colors.borderColor};
    `;
  }}
`;

export {Instances, Content, SplitPane, Pane, PaneHeader, Separator};
