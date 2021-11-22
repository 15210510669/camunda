/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  position: relative;
  width: 100%;
`;

const Input = styled.input`
  ${({theme}) => {
    const colors = theme.colors.login.input;
    return css`
      width: 100%;
      height: 48px;
      padding: 21px 8px 8px;
      border-radius: 3px;
      border: solid 1px ${colors.borderColor};
      font-family: IBM Plex Sans;
      font-size: 15px;
      color: ${colors.color};
      background-color: ${colors.backgroundColor};
      box-sizing: border-box;

      & + label {
        pointer-events: none;
        user-select: none;
        position: absolute;
        top: 0px;
        left: 0px;
        transform: translate(9px, 5px);
        font-size: 11px;
        color: ${colors.labelColor};
        transition: all ease-in-out 150ms;
      }
      &:placeholder-shown:not(:focus-within) + label {
        transform: translate(9px, 14px);
        font-size: 15px;
        font-style: italic;
      }
      &:focus-visible {
        outline: none;
        box-shadow: 0px 0px 0px 1px ${colors.focusInner},
          0px 0px 0px 4px ${colors.focusOuter};
      }
    `;
  }}
`;

export {Container, Input};
