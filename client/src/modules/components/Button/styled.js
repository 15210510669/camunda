/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {SIZE, COLOR} from './constants';
import {Colors, themed, themeStyle} from 'modules/theme';

const sizeStyle = ({size}) => {
  const smallSizeStyle = css`
    height: 22px;

    font-size: 13px;
  `;

  const mediumSizeStyle = css`
    height: 35px;
    width: 117px;

    font-size: 14px;
  `;

  const largeSizeStyle = css`
    height: 48px;
    width: 340px;

    font-size: 18px;
  `;

  const style = {
    small: smallSizeStyle,
    medium: mediumSizeStyle,
    large: largeSizeStyle,
  };

  return style[size];
};

const getBoxShadow = ({size}) => {
  const shadow = css`
    box-shadow: 0 2px 2px 0
      ${themeStyle({
        dark: 'rgba(0, 0, 0, 0.35)',
        light: 'rgba(0, 0, 0, 0.08)',
      })};
  `;

  return size === SIZE.SMALL ? '' : shadow;
};

const colorStyle = ({color, size}) => {
  const styles = {
    [COLOR.SECONDARY]: css`
      background-color: ${themeStyle({
        dark: Colors.darkButton03,
        light: Colors.lightButton04,
      })};

      border: 1px solid
        ${themeStyle({
          dark: Colors.uiDark05,
          light: Colors.uiLight03,
        })};

      color: ${themeStyle({
        dark: Colors.uiLight02,
        light: 'rgba(69, 70, 78, 0.9)',
      })};

      &:hover {
        background-color: ${themeStyle({
          dark: Colors.darkButton04,
          light: Colors.lightButton05,
        })};

        border-color: ${themeStyle({
          dark: Colors.darkButton05,
          light: Colors.uiLight03,
        })};

        color: ${({theme}) => theme === 'light' && 'rgba(69, 70, 78, 0.9)'};
      }

      &:active {
        background-color: ${themeStyle({
          dark: Colors.darkButton06,
          light: Colors.lightButton06,
        })};

        border-color: ${themeStyle({
          dark: Colors.darkButton07,
          light: Colors.uiLight03,
        })};

        color: ${({theme}) => theme === 'light' && 'rgba(49, 50, 56, 0.9)'};
      }

      &:disabled {
        background-color: ${({theme}) =>
          theme === 'light' && Colors.lightButton04};

        color: ${themeStyle({
          dark: 'rgba(247, 248, 250, 0.5)',
          light: 'rgba(69, 70, 78, 0.5)',
        })};
      }
    `,
    [COLOR.PRIMARY]: css`
      box-shadow: ${size === SIZE.SMALL
        ? ''
        : '0 2px 2px 0 rgba(0, 0, 0, 0.35)'};
      background-color: ${Colors.selections};
      border: 1px solid ${Colors.primaryButton03};
      color: ${Colors.uiLight04};

      &:hover {
        background-color: ${Colors.primaryButton03};
        border-color: ${Colors.primaryButton04};
      }

      &:focus {
        box-shadow: 0 0 0 1px ${Colors.darkLinkHover},
          0 0 0 4px ${Colors.focusOuter};
      }

      &:active {
        background-color: ${Colors.primaryButton04};
        border-color: ${Colors.primaryButton05};
      }

      &:disabled {
        background-color: ${Colors.primaryButton02};
        color: rgba(253, 253, 254, 0.8);
        border-color: ${Colors.primaryButton01};
        box-shadow: none;
      }
    `,
    [COLOR.MAIN]: css`
      color: ${themeStyle({
        dark: Colors.uiLight02,
        light: 'rgba(69, 70, 78, 0.9)',
      })};

      background-color: ${themeStyle({
        dark: Colors.uiDark05,
        light: Colors.uiLight05,
      })};

      border: 1px solid
        ${themeStyle({
          dark: Colors.uiDark06,
          light: Colors.uiLight03,
        })};

      &:hover {
        background-color: ${themeStyle({
          dark: '#6b6f74',
          light: '#cdd4df',
        })};
        border-color: ${themeStyle({
          dark: Colors.darkButton02,
          light: '#9ea9b7',
        })};
      }

      &:focus {
        border-color: ${themeStyle({
          dark: Colors.uiDark06,
          light: Colors.uiLight03,
        })};
      }

      &:active {
        background-color: ${themeStyle({
          dark: Colors.uiDark04,
          light: Colors.uiLight03,
        })};
        border-color: ${themeStyle({
          dark: Colors.uiDark05,
          light: '#88889a',
        })};
      }

      &:disabled {
        cursor: not-allowed;

        background-color: ${themeStyle({
          dark: '#34353a',
          light: '#f1f2f5',
        })};
        border-color: ${themeStyle({
          dark: Colors.uiDark05,
          light: Colors.uiLight03,
        })};
        color: ${themeStyle({
          dark: 'rgba(247, 248, 250, 0.5)',
          light: 'rgba(69, 70, 78, 0.5)',
        })};
        box-shadow: none;
      }
    `,
  };
  return styles[color] || styles[COLOR.MAIN];
};

export const Button = themed(styled.button`
  border-radius: ${({size}) => (size === SIZE.SMALL ? '11px' : '3px')};
  ${(props) => getBoxShadow(props)};
  font-family: IBMPlexSans;
  font-weight: 600;

  ${colorStyle};
  ${sizeStyle};
`);
