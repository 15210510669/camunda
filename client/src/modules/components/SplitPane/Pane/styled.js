/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import Panel from 'modules/components/Panel';
import CollapseButton from 'modules/components/CollapseButton';
import {EXPAND_STATE} from 'modules/constants';
import withStrippedProps from 'modules/utils/withStrippedProps';

const isCollapsed = (expandState) => expandState === EXPAND_STATE.COLLAPSED;

export const Pane = styled(
  withStrippedProps([
    'onAddToOpenSelection',
    'onAddNewSelection',
    'onAddToSelectionById',
    'onFlowNodeSelection',
  ])(Panel)
)`
  ${({expandState}) => (isCollapsed(expandState) ? '' : `flex-grow: 1;`)};
`;

export const Body = Panel.Body;

export const Footer = Panel.Footer;

export const PaneCollapseButton = styled(CollapseButton)`
  margin: 0;
  margin-top: 3px;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const ButtonsContainer = styled.div`
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
`;
