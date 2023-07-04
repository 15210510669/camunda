/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {StructuredList as BaseStructuredList} from 'modules/components/Carbon/StructuredList';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/Carbon/EmptyMessage';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/Carbon/ErrorMessage';

const Container = styled.div`
  height: 100%;
`;

const Panel = styled.section`
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
`;

const Title = styled.h2`
  ${styles.productiveHeading02}
  color: var(--cds-text-secondary);
  margin: var(--cds-spacing-05) 0 0 var(--cds-spacing-05);
`;

const StructuredList = styled(BaseStructuredList)`
  margin-top: var(--cds-spacing-05);
`;

const messageStyles = css`
  align-self: center;
  justify-self: center;
`;

const EmptyMessage = styled(BaseEmptyMessage)`
  ${messageStyles}
`;

const ErrorMessage = styled(BaseErrorMessage)`
  ${messageStyles}
`;

export {Container, Panel, Title, StructuredList, EmptyMessage, ErrorMessage};
