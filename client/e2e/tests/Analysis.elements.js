/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a').withText('Analysis');
export const flowNode = (id) => Selector(`[data-element-id="${id}"]`);
export const statisticsDiagram = Selector('.Statistics canvas');
export const endEventOverlay = Selector('.DiagramBehavior__end-event-statistics');
export const gatewayCancelButton = Selector('[name="gateway"] button');
export const gatewayInput = Selector('[name="gateway"] .content');
export const endEventInput = Selector('[name="endEvent"] .content');
export const branchAnalysisLink = Selector('.NavItem').withText('Branch Analysis');
export const heatmapEl = Selector('svg .viewport image');
export const tooltipDetailsButton = Selector('.Tooltip button').withText('View Details');
export const chart = Selector('.DurationChart canvas');
export const commonVariablesButton = Selector('.ButtonGroup .Button').withText(
  'Common Significant Variables Table'
);
export const variablesTable = Selector('.VariablesTable .Table');
export const modal = Selector('.Modal__content-container');
