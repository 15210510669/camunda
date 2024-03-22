/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {rem} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';
import {BodyCompact} from 'modules/components/FontTokens';
import {NavLink} from 'react-router-dom';
import styled, {css} from 'styled-components';

const ENTRY_DEFAULT_BORDER_WIDTH = 1;
const ENTRY_SELECTED_BORDER_WIDTH = 4;
const ENTRY_FOCUSED_BORDER_WIDTH = 2;

function getEntryPadding(options?: {
  top?: number;
  right?: number;
  bottom?: number;
  left?: number;
}) {
  const {top = 0, right = 0, bottom = 0, left = 0} = options ?? {};

  return css`
    ${({theme}) => css`
      padding: calc(${theme.spacing05} - ${top}px)
        calc(${theme.spacing05} - ${right}px)
        calc(${theme.spacing05} - ${bottom}px)
        calc(${theme.spacing05} - ${left}px);
    `}
  `;
}

type LabelProps = {
  $variant: 'primary' | 'secondary';
};

const Label = styled.span<LabelProps>`
  ${({theme, $variant}) => css`
    color: var(--cds-text-${$variant});
    ${theme.label01};
  `}
`;

type RowProps = {
  $direction?: 'row' | 'column';
};

const Row = styled.div<RowProps>`
  ${({$direction = 'column'}) => css`
    min-height: ${rem(20)};
    display: flex;
    flex-direction: ${$direction};
    justify-content: ${$direction === 'row' ? 'space-between' : 'center'};
    overflow: hidden;

    & ${Label}, & ${BodyCompact} {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `}
`;

const TaskLink = styled(NavLink)`
  all: unset;
  height: 100%;
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
`;

const Stack = styled(BaseStack)`
  width: 100%;
  height: 100%;
`;

const Container = styled.article`
  cursor: pointer;
  min-height: ${rem(144)};

  &.active ${TaskLink} {
    background-color: var(--cds-layer-selected);
    border-left: ${ENTRY_SELECTED_BORDER_WIDTH}px solid
      var(--cds-border-interactive);
    ${getEntryPadding({
      left: ENTRY_SELECTED_BORDER_WIDTH,
    })}
  }

  &.active:last-child ${TaskLink} {
    ${getEntryPadding({
      left: ENTRY_SELECTED_BORDER_WIDTH,
    })}
  }

  &.active + & ${TaskLink}:not(:focus) {
    border-top: none;
    ${getEntryPadding()}
  }

  &:not(.active) {
    &:hover ${TaskLink} {
      background-color: var(--cds-layer-hover);
    }

    &:last-child ${TaskLink} {
      border-bottom: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
        var(--cds-border-subtle-selected);
      ${getEntryPadding({
        top: ENTRY_DEFAULT_BORDER_WIDTH,
        bottom: ENTRY_DEFAULT_BORDER_WIDTH,
      })}
    }

    & ${TaskLink} {
      border-top: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
        var(--cds-border-subtle-selected);
      ${getEntryPadding({
        top: ENTRY_DEFAULT_BORDER_WIDTH,
      })}
    }
  }

  & ${TaskLink}:focus {
    border: none;
    ${getEntryPadding()}
    outline: ${ENTRY_FOCUSED_BORDER_WIDTH}px solid var(--cds-focus);
    outline-offset: -${ENTRY_FOCUSED_BORDER_WIDTH}px;
  }

  &:last-child ${TaskLink}:focus {
    ${getEntryPadding()}
  }

  &:first-child ${TaskLink} {
    border-top-color: transparent;
  }
`;

const SkeletonContainer = styled.article`
  min-height: ${rem(136)};
  max-height: ${rem(136)};

  &:last-child > * {
    border-bottom: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
      var(--cds-border-subtle-selected);
    ${getEntryPadding({
      top: ENTRY_DEFAULT_BORDER_WIDTH,
      bottom: ENTRY_DEFAULT_BORDER_WIDTH,
    })}
  }

  & > * {
    border-top: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
      var(--cds-border-subtle-selected);
    ${getEntryPadding({
      top: ENTRY_DEFAULT_BORDER_WIDTH,
    })}
  }
`;

const DateLabel = styled(Label)`
  padding: var(--cds-spacing-01) var(--cds-spacing-01);
`;

export {Row, Label, TaskLink, Stack, Container, SkeletonContainer, DateLabel};
