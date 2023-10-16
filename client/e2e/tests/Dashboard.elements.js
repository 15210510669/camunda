/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const body = Selector('body');
export const dashboard = Selector('.ListItem.dashboard');
export const reportEditButton = Selector('.EditButton');
export const reportDeleteButton = Selector('.DeleteButton');
export const reportResizeHandle = Selector('.react-resizable-handle');
export const dashboardName = Selector('.DashboardView .name');
export const externalSourceLink = Selector('button').withText('External Website');
export const externalSourceInput = Selector('.externalInput');
export const addTileButton = Selector('.CreateTileModal button').withText('Add Tile');
export const textReportLink = Selector('button').withText('Text');
export const textReportInput = Selector('.editor');
export const textReportToolButton = (title) => Selector(`.Button[title=${title}]`);
export const textReportInsertDropdown = Selector('.InsertOptions');
export const textReportUrlInput = Selector('.InsertModal input').nth(0);
export const textReportAltInput = Selector('.InsertModal input').nth(1);
export const textReportInsertAddButton = Selector('.InsertModal button').withText('Add');
export const blankReportButton = Selector('.Button').withText('Blank report');
export const externalReport = Selector('iframe', {timeout: 60000});
export const textReport = Selector('.TextTile .editor');
export const textReportField = (element) => textReport.find(element);
export const exampleHeading = Selector('h1');
export const fullscreenButton = Selector('.fullscreen-button');
export const header = Selector('.cds--header');
export const themeButton = Selector('.theme-toggle');
export const fullscreenContent = Selector('.fullscreen');
export const shareFilterCheckbox = Selector('.ShareEntity input[type="checkbox"]');
export const autoRefreshButton = Selector('.tools .Dropdown').withText('Auto Refresh');
export const reportModalOptionsButton = Selector('.CreateTileModal').find('.optionsButton');
export const reportModalDropdownOption = Selector('.CreateTileModal').find('.DropdownOption');
export const addFilterButton = Selector('.Button').withText('Add a filter');
export const instanceStateFilter = Selector('.InstanceStateFilter .Popover .Button');
export const selectionFilter = Selector('.SelectionFilter .Popover .Button');
export const switchElement = (text) => Selector('.Switch').withText(text);
export const dashboardContainer = Selector('.Dashboard');
export const templateModalProcessTag = Selector('.TemplateModal .Tag');
export const templateOption = (text) =>
  Selector('.Modal .templateContainer .Button').withText(text);
export const reportTile = Selector('.OptimizeReportTile');
export const customValueAddButton = Selector('.customValueAddButton');
export const typeaheadInput = Selector('.Typeahead .Input');
export const alertsDropdown = Selector('.AlertsDropdown .Button');
export const alertDeleteButton = Selector('.AlertModal .deleteButton');
export const collectionLink = Selector('.NavItem a').withText('New Collection');
export const dashboardsLink = Selector('.NavItem a').withText('Dashboards');
export const createCopyButton = Selector('.create-copy');
