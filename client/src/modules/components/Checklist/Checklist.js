/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import classnames from 'classnames';

import {Input, LabeledInput, Tag, LoadingIndicator, Icon} from 'components';
import {t} from 'translation';

import './Checklist.scss';

export default function Checklist({
  onSearch = () => {},
  selectedItems,
  allItems,
  onChange,
  formatter,
  loading,
  labels = {
    search: t('common.multiSelect.search'),
    empty: t('common.multiSelect.empty'),
  },
  headerHidden,
  preItems,
}) {
  const [query, setQuery] = useState('');

  if (!allItems) {
    return <LoadingIndicator />;
  }

  const data = formatter(allItems, selectedItems);
  const allSelected = data.every(({checked}) => checked);
  const allDeselected = selectedItems.length === 0;

  const filteredData = data.filter(({label}) => label.toLowerCase().includes(query.toLowerCase()));
  const allSelectedInView = filteredData.every(({checked}) => checked);

  const updateItems = (itemId, checked) => {
    if (checked) {
      const itemToSelect = allItems.find((item) => getIdentifier(item) === itemId);
      onChange([...selectedItems, itemToSelect]);
    } else {
      onChange(selectedItems.filter((item) => getIdentifier(item) !== itemId));
    }
  };

  const selectAll = () => onChange(allItems);

  const selectAllInView = () => {
    const selectableIds = filteredData.filter(({checked}) => !checked).map(({id}) => id);
    const itemsToSelect = allItems.filter((item) => selectableIds.includes(getIdentifier(item)));
    return onChange([...selectedItems, ...itemsToSelect]);
  };

  const deselectAll = () => onChange([]);

  const deselectAllInView = () => {
    const selectedIds = filteredData.filter(({checked}) => checked).map(({id}) => id);
    const itemsToRemove = selectedItems.filter(
      (item) => !selectedIds.includes(getIdentifier(item))
    );
    return onChange(itemsToRemove);
  };

  return (
    <div className="Checklist">
      {!headerHidden && (
        <div className="header">
          {data.length > 1 && (
            <LabeledInput
              className="selectAll"
              ref={(input) => {
                if (input != null) {
                  input.indeterminate = !allSelected && !allDeselected;
                }
              }}
              checked={allSelected}
              type="checkbox"
              label={t('common.selectAll')}
              onChange={({target: {checked}}) => (checked ? selectAll() : deselectAll())}
            />
          )}
          <div className="searchInputContainer">
            <Input
              value={query}
              className="searchInput"
              placeholder={labels.search}
              type="text"
              onChange={(evt) => {
                setQuery(evt.target.value);
                onSearch(evt.target.value);
              }}
              onClear={() => {
                setQuery('');
                onSearch('');
              }}
            />
            <Icon className="searchIcon" type="search" size="20" />
          </div>
          {selectedItems.length > 0 && (
            <Tag onRemove={deselectAll}>{selectedItems.length} Selected</Tag>
          )}
        </div>
      )}
      <div className="itemsList">
        {loading && <LoadingIndicator />}
        {!loading && (
          <>
            {filteredData.length === 0 && <p>{labels.empty}</p>}
            {query && filteredData.length > 1 && (
              <LabeledInput
                className={classnames({highlight: allSelectedInView})}
                type="checkbox"
                checked={allSelectedInView}
                label={t('common.multiSelect.selectAll')}
                onChange={({target: {checked}}) =>
                  checked ? selectAllInView() : deselectAllInView()
                }
              />
            )}
            {preItems}
            {filteredData.map(({id, label, checked, disabled}) => (
              <LabeledInput
                className={classnames({highlight: checked && !disabled})}
                disabled={disabled}
                key={id}
                type="checkbox"
                checked={checked}
                label={label}
                onChange={({target: {checked}}) => updateItems(id, checked)}
              />
            ))}
          </>
        )}
      </div>
    </div>
  );
}

function getIdentifier(item) {
  if (typeof item === 'object' && item !== null) {
    return item.key || item.id;
  }

  return item;
}
