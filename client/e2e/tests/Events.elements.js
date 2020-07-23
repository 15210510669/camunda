/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a').withText('Event Based Processes');
export const createDropdown = Selector('.Events .Dropdown');
export const dropdownOption = (text) => Selector('.DropdownOption').withText(text);
export const fileInput = Selector('input').withAttribute('type', 'file');
export const entity = (title) => Selector('.name .entity').withText(title);
export const nameEditField = Selector('.EntityNameForm .name-input');
export const firstEl = Selector('.djs-hit');
export const activityTask = Selector('.bpmn-icon-task');
export const saveButton = Selector('.save-button');
export const processName = Selector('.ProcessView .name');
export const editButton = Selector('.edit-button');
export const addSource = Selector('button.addProcess');
export const modalContainer = Selector('.Modal__content-container');
export const processTypeahead = Selector('.selectionPanel');
export const variableTypeahead = Selector('.FormGroup');
export const optionsButton = (typeahead) => typeahead.find('.Typeahead .optionsButton');
export const typeaheadInput = (typeahead) => typeahead.find('.Typeahead input');
export const typeaheadOption = (typeahead, text) =>
  typeahead.find('.Typeahead .DropdownOption').withText(text);
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const externalEvents = Selector('.Button').withText('External Events');
export const startNode = Selector('g[data-element-id=StartEvent_1]');
export const startAndEndEvents = Selector('input[type=radio]').nth(3);
export const startEvent = Selector('.StartEvent_1 input[type=checkbox]');
export const activity = Selector('g[data-element-id=Activity_1s5va7f]');
export const bankStart = Selector('.BankTransferStart input[type=checkbox]');
export const bankEnd = Selector('.BankTransferEnd input[type=checkbox]');
export const endNode = Selector('g[data-element-id=Event_0m3kxux]');
export const endEvent = Selector('.invoiceProcessed input[type=checkbox]');
export const eventsTable = Selector('.EventTable');
export const publishButton = Selector('.publish-button');
export const permissionButton = Selector('.permission button');
export const usersTypeahead = Selector('.UserTypeahead');
export const buttonWithText = (text) => Selector('.Modal__content-container button').withText(text);
export const zoomButton = Selector('.zoomIn');
export const diagram = Selector('.ProcessView .BPMNDiagram');
export const businessKey = Selector('.label').withText('Business Key').prevSibling();
