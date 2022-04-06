/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Panel} from 'modules/components/Panel';
import {CmText, CmDropdown, CmSelect} from '@camunda-cloud/common-ui-react';

const FiltersForm = styled.form`
  width: 100%;
  height: 100%;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
`;

const OrderedFilters = styled.div`
  display: flex;
  flex-direction: column-reverse;
`;

const ResetButtonContainer = styled(Panel.Footer)`
  ${({theme}) => {
    const shadow = theme.shadows.filters.resetButtonContainer;

    return css`
      display: flex;
      justify-content: center;
      width: 100%;
      box-shadow: ${shadow};
      border-radius: 0;
    `;
  }}
`;

const Fields = styled.div`
  ${({theme}) => {
    return css`
      overflow-y: auto;
      flex-grow: 1;
      padding: 23px 20px 8px 20px;
      background-color: ${theme.colors.itemEven};
    `;
  }}
`;

const ProcessHeader = styled(CmText)`
  display: block;
  padding-bottom: 5px;
`;

const StatesHeader = styled(CmText)`
  display: block;
  margin-bottom: 9px;
`;

const InstanceStates = styled.div`
  padding-top: 17px;
`;

const MoreFiltersDropdown = styled(CmDropdown)`
  display: flex;
  justify-content: flex-end;
  margin: 0 3px 16px 0;
`;

const Select = styled(CmSelect)`
  padding-bottom: 5px;
`;

export {
  FiltersForm,
  ResetButtonContainer,
  Fields,
  ProcessHeader,
  StatesHeader,
  InstanceStates,
  OrderedFilters,
  MoreFiltersDropdown,
  Select,
};
