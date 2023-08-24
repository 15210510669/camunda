/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {
  PAGE_TOP_PADDING,
  COLLAPSABLE_PANEL_HEADER_HEIGHT,
} from 'modules/constants';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';

const PanelHeader = styled(BasePanelHeader)`
  padding-right: 0;
`;

const Container = styled.div`
  display: grid;
  grid-template-rows: ${COLLAPSABLE_PANEL_HEADER_HEIGHT} 1fr;
  height: calc(100vh - ${PAGE_TOP_PADDING}px);
  position: relative;
  width: 100%;
  background: var(--cds-layer);

  .dmn-drd-container .djs-visual rect {
    stroke: var(--cds-icon-secondary) !important;
    fill: var(--cds-layer) !important;
  }

  .dmn-drd-container .djs-label {
    fill: var(--cds-text-primary) !important;
  }

  .dmn-drd-container .djs-connection path {
    stroke: var(--cds-icon-secondary) !important;
  }

  marker#information-requirement-end {
    fill: var(--cds-icon-secondary) !important;
  }

  .ope-selectable {
    cursor: pointer;

    &.hover .djs-outline {
      stroke: var(--cds-link-inverse);
      stroke-width: 2px;
    }
  }

  .ope-selected {
    .djs-outline {
      stroke: var(--cds-link-inverse);
      stroke-width: 2px;
    }

    .djs-visual rect {
      fill: var(--cds-highlight) !important;
    }
  }
`;

export {PanelHeader, Container};
