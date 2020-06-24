/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Panel from 'modules/components/Panel';
import Operations from 'modules/components/Operations';
import IconButton from 'modules/components/IconButton';
import BasicInput from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import Modal from 'modules/components/Modal';

import {ReactComponent as DefaultEdit} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ReactComponent as DefaultModal} from 'modules/components/Icon/modal.svg';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';

import EmptyPanelComponent from 'modules/components/EmptyPanel';
import DefaultButton from 'modules/components/Button';

export const Spinner = styled(Operations.Spinner)`
  margin-top: 4px;
`;

export const Variables = themed(styled(Panel)`
  flex: 1;
  font-size: 14px;

  border-left: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)',
  })};
`);

export const VariablesContent = styled(Panel.Body)`
  position: absolute;

  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  border-top: none;
`;

export const TableScroll = styled.div`
  overflow-y: auto;
  height: 100%;
  margin-top: 45px;
  margin-bottom: 40px;
`;

export const Placeholder = themed(styled.span`
  position: absolute;
  text-align: center;
  top: 40%;
  width: 100%;
  font-size: 14px;
  color: ${themeStyle({
    dark: '#dedede',
    light: Colors.uiLight06,
  })};
  padding: 0 20px;
`);

export const TD = themed(styled.td`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)',
  })};
  font-weight: ${(props) => (props.isBold ? 'bold' : 'normal')};

  padding-top: 5px
  padding-bottom: 5px;
  padding-left: 17px;
  padding-right: 9px;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }
  vertical-align: top;
`);

export const THead = themed(styled.thead`
  tr:first-child {
    position: absolute;
    width: 100%;
    top: 0;

    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05,
      })};
    background: ${themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04,
    })};
    z-index: 2;
    border-top: none;
    height: 45px;
    border-top: none;
    > th {
      padding-top: 21px;
    }
    > th:first-child {
      min-width: 226px;
    }
  }
`);

export const VariableName = styled.span`
  height: 100%;

  padding-top: 4px;
  margin-top: 3px;
  line-height: 18px;

  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
`;

const inputMargin = css`
  margin: 4px 0 4px 7px;
`;

export const TextInput = styled(BasicInput)`
  height: 30px;

  padding-top: 7px;
  ${inputMargin};

  font-size: 14px;
  max-width: 181px;
`;

export const DisplayText = styled.div`
  line-height: 18px;
  word-break: break-word;

  margin: 11px 94px 11px 0;
  max-height: 76px;

  overflow-y: auto;
  overflow-wrap: break-word;
`;

const textAreaStyles = css`
  line-height: 18px;
  resize: vertical;
  font-size: 14px;

  min-height: 30px;
  max-height: 84px;

  ${inputMargin};
  width: 100%;
`;

export const AddTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
`;

export const EditTextarea = styled(BasicTextarea)`
  ${textAreaStyles};
`;

export const EditButtonsTD = styled.td`
  padding-right: 21px;
  padding-top: 8px;
  display: flex;
  justify-content: flex-end;
  width: 100px;
`;

export const AddButtonsTD = styled(EditButtonsTD)`
  padding-top: 9px;
`;

export const EditInputTD = styled.td`
  position: relative;

  &:not(:nth-child(2)) {
    white-space: nowrap;
  }

  &:nth-child(2) {
    width: 100%;
  }

  vertical-align: top;
`;

export const DisplayTextTD = styled(TD)`
  width: 100%;
`;

export const EditButton = styled(IconButton)`
  margin-left: 10px;
  z-index: 0;

  svg {
    margin-top: 4px;
  }

  &:disabled,
  &:disabled :hover {
    svg {
      color: ${themeStyle({
        dark: Colors.uiLight02,
        light: Colors.uiDark05,
      })};
      opacity: 0.5;
    }

    &:before {
      background-color: transparent;
    }
  }
`;

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${themeStyle({
    dark: Colors.uiLight02,
    light: Colors.uiDark04,
  })};
`;

export const CloseIcon = themed(styled(DefaultClose)`
  ${iconStyle}
`);

export const CheckIcon = themed(styled(DefaultCheck)`
  ${iconStyle}
`);

export const EditIcon = themed(styled(DefaultEdit)`
  ${iconStyle}
`);

export const ModalIcon = themed(styled(DefaultModal)`
  ${iconStyle}
`);

export const ModalBody = themed(styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`);

export const CodeLine = themed(styled.p`
  margin: 3px;
  margin-left: 0;
  line-height: 14px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06,
  })};
  font-family: IBMPlexMono;
  font-size: 14px;

  &:before {
    font-size: 12px;
    box-sizing: border-box;
    text-align: right;
    counter-increment: line;
    content: counter(line);
    color: ${themeStyle({
      dark: '#ffffff',
      light: Colors.uiLight06,
    })};
    display: inline-block;
    width: 35px;
    opacity: ${themeStyle({
      dark: 0.5,
      light: 0.65,
    })};
    padding-right: 11px;
    -webkit-user-select: none;
  }
`);

export const LinesSeparator = themed(styled.span`
  position: absolute;
  top: 0;
  left: 33px;
  height: 100%;
  width: 1px;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05,
  })};
`);

export const EmptyPanel = styled(EmptyPanelComponent)`
  position: absolute;
  top: 20px;
  z-index: 1;
`;

export const Button = styled(DefaultButton)`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 22px;
  width: 119px;
  margin-left: 16px;
`;

export const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

export const Footer = styled(Panel.Footer)`
  position: absolute;
  bottom: 0;

  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 41px;
`;
