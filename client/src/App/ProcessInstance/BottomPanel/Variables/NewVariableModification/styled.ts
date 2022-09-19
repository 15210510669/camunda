/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {TextField} from 'modules/components/TextField';
import {ReactComponent as DefaultDelete} from 'modules/components/Icon/delete.svg';
import {ActionButtons as DefaultActionButtons} from 'modules/components/ActionButtons';
import {TD} from '../styled';

const DeleteIcon = styled(DefaultDelete)`
  ${({theme}) => {
    const colors = theme.colors.variables.icons;

    return css`
      width: 16px;
      height: 16px;
      object-fit: contain;
      color: ${colors.color};
    `;
  }}
`;

const NameField = styled(TextField)`
  padding-left: 9px;
  margin: 4px 0px;
`;

const ValueField = styled(TextField)`
  margin: 4px 0 4px 0;
`;

const FlexContainer = styled.div`
  display: flex;
`;

const ActionButtons = styled(DefaultActionButtons)`
  padding: 8px 0 0 4px;
  align-self: flex-start;
`;

const NewValueTD = styled(TD)`
  vertical-align: top;
`;

export {
  NameField,
  ValueField,
  FlexContainer,
  DeleteIcon,
  ActionButtons,
  NewValueTD,
};
