/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, createGlobalStyle} from 'styled-components';
import {CARBON_STYLES} from '@bpmn-io/form-js-carbon-styles';

const FormCustomStyling = createGlobalStyle`
  ${() => css`
    ${CARBON_STYLES}
    .fjs-container {
      .fjs-form-field button[type='submit'],
      .fjs-powered-by {
        display: none;
      }
    }
  `}
`;

const FormRoot = styled.div`
  width: 100%;
`;

const FormContainer = styled.div`
  width: 100%;
  background-color: var(--cds-layer);
  padding: var(--cds-spacing-08) var(--cds-spacing-06);
`;

const Container = styled.div`
  width: 100%;
`;

const SubmitButtonRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--cds-spacing-04) var(--cds-spacing-06);
`;

const FormSkeletonContainer = styled.div`
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(4, min-content);
  grid-gap: var(--cds-spacing-04);

  & > :nth-child(1) {
    grid-area: 1 / 1 / 2 / 2;
  }
  & > :nth-child(2) {
    grid-area: 2 / 1 / 3 / 3;
  }
  & > :nth-child(3) {
    grid-area: 3 / 1 / 4 / 2;
  }
  & > :nth-child(4) {
    grid-area: 3 / 2 / 4 / 3;
  }
  & > :nth-child(5) {
    grid-area: 4 / 1 / 5 / 3;
  }
`;

export {
  FormCustomStyling,
  FormRoot,
  Container,
  FormContainer,
  SubmitButtonRow,
  FormSkeletonContainer,
};
