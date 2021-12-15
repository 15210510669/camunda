/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  position: relative;
`;

const LicenseTag = styled.button`
  ${({theme}) => css`
    height: 22px;
    margin: 1px 0;
    padding: 3px 14px 4px;
    border-radius: 3px;
    background-color: ${theme.colors.orange};
    font-size: 12px;
    font-weight: 600;
    color: ${theme.colors.white};
  `}
`;

const LicenseNoteBox = styled.div`
  ${({theme}) => {
    const licenseColors = theme.colors.header.license;
    return css`
      position: absolute;
      top: 32px;
      width: 270px;
      right: 90px;
      padding: 12px 12px 10px 12px;

      font-size: 14px;
      color: ${licenseColors.color};
      background-color: ${licenseColors.backgroundColor};

      border-radius: 3px;
      box-shadow: 0 0 2px 0 rgba(0, 0, 0, 0.2);
      border: solid 1px ${licenseColors.borderColor};

      z-index: 6;

      &:before,
      &:after {
        position: absolute;
        content: ' ';
        pointer-events: none;
        color: transparent;
        border-style: solid;
        bottom: 100%;
        right: 20px;
        z-index: 2;
      }

      &:before {
        border-bottom-color: ${licenseColors.arrowStyle.before.borderColor};
        border-width: 8px;
        margin-right: -8px;
      }

      &:after {
        border-bottom-color: ${licenseColors.arrowStyle.after.borderColor};
        border-width: 7px;
        margin-right: -7px;
      }
    `;
  }};
`;

export {LicenseTag, LicenseNoteBox, Container};
