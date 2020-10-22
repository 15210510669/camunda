/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import StateIcon from 'modules/components/StateIcon';

const NodeStateIcon = styled(StateIcon)`
  ${({$indentationMultiplier}) => {
    return css`
      top: 6px;
      left: ${$indentationMultiplier ? -$indentationMultiplier * 32 : 5}px;
    `;
  }}
`;

const connectionDotStyles = ({theme}) => {
  const colors = theme.colors.flowNodeInstancesTree.connectionDot;

  return css`
    height: 5px;
    width: 5px;
    border-radius: 50%;
    background: ${colors.color};
  `;
};

const NodeDetails = styled.div`
  ${({theme, isSelected, showConnectionDot}) => {
    const colors = theme.colors.flowNodeInstancesTree.nodeDetails;

    return css`
      display: flex;
      align-items: center;
      position: absolute;
      color: ${isSelected ? colors.selected.color : colors.color};

      &:before {
        content: '';
        position: absolute;
        left: -51px;
        top: 13px;
        ${showConnectionDot ? connectionDotStyles : ''};
      }
    `;
  }}
`;

const Ul = styled.ul`
  ${({theme, showConnectionLine}) => {
    const colors = theme.colors.flowNodeInstancesTree.ul;

    return css`
      position: relative;
      ${showConnectionLine
        ? css`
            &:before {
              content: '';
              position: absolute;
              /* line ends 10px above the bottom of the element */
              height: calc(100% - 10px);
              width: 1px;
              left: -17px;
              background: ${colors.backgroundColor};
            }

            /* show a final dot at the end of each connection line */
            &:after {
              content: '';
              position: absolute;
              bottom: 9px;
              left: -19px;
              ${connectionDotStyles};
            }
          `
        : ''};
    `;
  }}
`;

const Li = styled.li`
  ${({treeDepth}) => {
    return css`
      margin-left: 32px;

      /* adjust focus position for first tree elements */
      &:first-child > div:nth-child(2) > button {
        ${treeDepth === 1
          ? css`
              height: calc(100% - 5px);
              top: 4px;
            `
          : ''};
      }

      &:first-child > div:nth-child(2) > div > div {
        ${treeDepth === 1
          ? css`
              border-top-width: 0px;
            `
          : ''};
      }
    `;
  }}
`;

export {NodeStateIcon, NodeDetails, Ul, Li};
