/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const typeahead = Selector('.Modal__content .Typeahead');
export const multiSelect = Selector('.Modal__content .MultiSelect');
export const typeaheadInput = Selector('.Modal__content .Typeahead .Input');
export const typeaheadOption = (text) => typeahead.find('.DropdownOption').withText(text);
export const multiSelectOption = (text) => multiSelect.find('.DropdownOption').withText(text);
export const multiSelectOptionNumber = (idx) => multiSelect.find('.DropdownOption').nth(idx);
export const variableFilterOperatorButton = (text) =>
  Selector('.Modal .buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .ValueListInput input').nth(0);
export const dateFilterTypeSelect = Selector('.DateRangeInput .Dropdown');
export const dateFilterTypeOption = (text) =>
  Selector('.DateRangeInput .DropdownOption').withText(text);
export const dateFilterStartInput = Selector('.DateFields .PickerDateInput:first-child input');
export const dateFilterEndInput = Selector('.DateFields .PickerDateInput:last-child input');
export const pickerDate = (number) =>
  Selector('.DateFields .rdrMonths .rdrMonth:first-child .rdrDay').withText(number);
export const infoText = Selector('.Modal__content .tip');
export const dateTypeSelect = Selector('.selectGroup > .Select');
export const unitSelect = Selector('.unitSelection .Select');
export const customDateInput = Selector('.unitSelection').find('input');
export const durationFilterOperator = Selector('.DurationFilter .Select');
export const durationFilterInput = Selector('.DurationFilter input[type="text"]');
export const modalCancel = Selector('.Modal .Button').withText('Cancel');
export const stringValues = Selector('.Checklist .itemsList');
export const firstMultiSelectValue = Selector('.Checklist .itemsList .LabeledInput .label');
export const multiSelectValue = (text) => firstMultiSelectValue.withText(text);
export const confirmButton = Selector('.Modal .Modal__actions .primary');
export const customValueCheckbox = Selector('.Modal .customValueCheckbox');
export const addValueButton = Selector('.Modal .customValueButton');
export const customValueInput = Selector('.Modal .customValueInput input');
export const addValueToListButton = Selector('.Modal .customValueInput button');
