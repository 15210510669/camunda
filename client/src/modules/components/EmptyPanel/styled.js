/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Check} from 'modules/components/Icon/check.svg';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

const EmptyPanel = styled.div`
  height: 100%;
  width: 100%;
`;

const LabelContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 58%;
`;

const Label = styled.span`
  ${({theme, type}) => {
    const colors = theme.colors.modules.emptyPanel;

    return css`
      ${type === 'info'
        ? css`
            color: ${colors.color};
            opacity: 0.8;
          `
        : css`
            color: ${theme.colors.incidentsAndErrors};
            opacity: 0.9;
          `}

      font-family: IBMPlexSans;
      font-size: 16px;
      padding-top: 5px;
    `;
  }}
`;

const CheckIcon = styled(Check)`
  ${({theme}) => {
    return css`
      width: 18px;
      height: 14px;
      fill: ${theme.colors.allIsWell};
      margin-right: 13px;
    `;
  }}
`;
const WarningIcon = styled(Warning)`
  ${({theme}) => {
    return css`
      width: 20px;
      height: 18px;
      fill: ${theme.colors.incidentsAndErrors};
      margin-right: 15px;
    `;
  }}
`;

export {EmptyPanel, LabelContainer, Label, CheckIcon, WarningIcon};
