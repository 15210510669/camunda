/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a[href="#/events/processes/"]');
export const createDropdown = Selector('.Events .Dropdown');
export const invoiceEventProcess = Selector('.ListItem').withText('Event Invoice process');
export const editAccess = (element) => element.find('.DropdownOption').withText('Edit Access');
export const fileInput = Selector('input').withAttribute('type', 'file');
export const entity = (title) => Selector('.name .entity').withText(title);
export const firstEl = Selector('.djs-hit');
export const activityTask = Selector('.bpmn-icon-task');
export const saveButton = Selector('.save-button');
export const processName = Selector('.ProcessView .name');
export const addSource = Selector('button.addProcess');
export const processTypeahead = Selector('.selectionPanel input[type=text]');
export const variableTypeahead = Selector('.FormGroup .Typeahead');
export const optionsButton = (typeahead) => typeahead.find('.optionsButton');
export const typeaheadInput = (typeahead) => typeahead.find('input');
export const typeaheadOption = (typeahead, text) =>
  typeahead.find('.DropdownOption').withText(text);
export const externalEvents = Selector('.cds--tabs button').withText('External Events');
export const startNode = Selector('g[data-element-id=StartEvent_1]');
export const startAndEndEvents = Selector('input[type=radio]').nth(3);
export const startEvent = Selector('.StartEvent_1 .cds--table-column-checkbox label');
export const activity = Selector('g[data-element-id=Activity_1s5va7f]');
export const bankStart = Selector('.BankTransferStart .cds--table-column-checkbox label');
export const bankEnd = Selector('.BankTransferEnd .cds--table-column-checkbox label');
export const endNode = Selector('g[data-element-id=Event_0m3kxux]');
export const endEvent = Selector('.invoiceProcessed .cds--table-column-checkbox label');
export const eventsTable = Selector('.EventTable');
export const publishButton = Selector('.publish-button');
export const permissionButton = Selector('.permission button');
export const addEventSourceBtn = Selector('.GenerationModal button').withText('Add Event Source');
export const zoomButton = Selector('.zoomIn');
export const diagram = Selector('.ProcessView .BPMNDiagram');
export const businessKey = Selector('.label').withText('Business Key').prevSibling();
export const externalEventsTab = Selector('.NavItem').withText('External Events');
export const eventCheckbox = (index) =>
  Selector('.Table tbody tr').nth(index).find('.cds--table-column-checkbox label');
export const selectionDropdown = Selector('.selectionActions button');
export const externalEventsTable = Selector('.Table');
export const externalEventgroup = (index) =>
  Selector('.ExternalSource tr .cds--table-column-checkbox').nth(index);
