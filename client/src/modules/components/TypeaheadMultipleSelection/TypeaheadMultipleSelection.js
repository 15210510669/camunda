/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Input, LoadingIndicator} from 'components';
import {formatters} from 'services';
import classnames from 'classnames';

import './TypeaheadMultipleSelection.scss';

export default class TypeaheadMultipleSelection extends React.Component {
  constructor() {
    super();
    this.state = {
      searchQuery: ''
    };
    this.dragPlaceHolder = document.createElement('li');
    this.dragPlaceHolder.className = 'placeholder';
  }

  mapSelectedValues = values => {
    const isDraggable = !!this.props.onOrderChange;
    return (
      values.length > 0 && (
        <div className="TypeaheadMultipleSelection__labeled-valueList">
          <p>Selected {this.props.label}: </p>
          <div
            onDragOver={isDraggable ? this.dragOver : undefined}
            className="TypeaheadMultipleSelection__values-sublist"
          >
            {values.map((value, idx) => {
              const dragProps = {
                draggable: true,
                onDragEnd: this.dragEnd,
                onDragStart: this.dragStart
              };
              return (
                <li
                  key={idx}
                  data-id={idx}
                  className={classnames('TypeaheadMultipleSelection__valueListItem', {
                    draggable: isDraggable
                  })}
                  {...(isDraggable ? dragProps : {})}
                >
                  <label>
                    <Input type="checkbox" checked value={idx} onChange={this.toggleSelected} />
                    {this.props.format(value)}
                  </label>
                  {this.props.customItemSettings && this.props.customItemSettings(value, idx)}
                </li>
              );
            })}
            <li className="endIndicator" data-id={values.length} />
          </div>
        </div>
      )
    );
  };

  dragStart = evt => {
    this.dragged = evt.currentTarget;
    evt.dataTransfer.effectAllowed = 'move';
    // firefox requires calling this function to start dragging
    evt.dataTransfer.setData('text/plain', '');
  };

  dragEnd = evt => {
    if (!this.over) {
      this.over = evt.target;
    }
    this.dragged.style.display = 'flex';

    const container = this.dragged.parentNode;
    if (container.contains(this.dragPlaceHolder)) {
      container.removeChild(this.dragPlaceHolder);
    }

    // update props
    let data = [...this.props.selectedValues];
    const from = +this.dragged.dataset.id;
    let to = +this.over.dataset.id;
    if (from < to) {
      to--;
    }
    data.splice(to, 0, data.splice(from, 1)[0]);
    this.props.onOrderChange(data);
    this.dragged = null;
  };

  dragOver = evt => {
    evt.preventDefault();
    if (!this.dragged || evt.target.className === 'placeholder') {
      return;
    }
    this.dragged.style.display = 'none';
    const listElement = evt.target.closest('li');
    this.over = listElement;
    if (listElement) {
      listElement.parentNode.insertBefore(this.dragPlaceHolder, listElement);
    }
  };

  toggleSelected = ({target: {value, checked}}) =>
    this.props.toggleValue(this.props.selectedValues[value], checked);

  mapAvaliableValues = (availableValues, selectedValues) => {
    return (
      <div className="TypeaheadMultipleSelection__labeled-valueList">
        <p>Available {this.props.label}: </p>
        <div className="TypeaheadMultipleSelection__values-sublist">
          {availableValues.map((value, idx) => {
            if (!selectedValues.includes(value)) {
              return (
                <li key={idx} className="TypeaheadMultipleSelection__valueListItem">
                  <label>
                    <Input
                      type="checkbox"
                      checked={selectedValues.includes(value)}
                      value={idx}
                      onChange={this.toggleAvailable}
                    />
                    {formatters.getHighlightedText(
                      this.props.format(value),
                      this.state.searchQuery
                    )}
                  </label>
                </li>
              );
            }
            return null;
          })}
        </div>
      </div>
    );
  };

  toggleAvailable = ({target: {value, checked}}) =>
    this.props.toggleValue(this.props.availableValues[value], checked);

  render() {
    const {availableValues, selectedValues, loading} = this.props;
    const input = (
      <div className="TypeaheadMultipleSelection__labeled-input">
        <Input
          className="TypeaheadMultipleSelection__input"
          placeholder={`Search for ${this.props.label}`}
          onChange={e => {
            this.setState({searchQuery: e.target.value});
            return this.props.setFilter(e);
          }}
        />
      </div>
    );
    const loadingIndicator = loading ? <LoadingIndicator /> : '';
    if (availableValues.length === 0) {
      return (
        <div className="TypeaheadMultipleSelection">
          {input}
          {loadingIndicator}
          <div className="TypeaheadMultipleSelection__valueList">
            {this.mapSelectedValues(selectedValues)}
          </div>
          <div className="TypeaheadMultipleSelection__valueList">
            <div className="TypeaheadMultipleSelection__labeled-valueList">
              <p>Available {this.props.label}: </p>
              <li className="TypeaheadMultipleSelection__no-items">
                {loading ? '' : `No matching ${this.props.label} found`}
              </li>
            </div>
          </div>
        </div>
      );
    }
    return (
      <div className="TypeaheadMultipleSelection">
        {input}
        {loadingIndicator}
        <div className="TypeaheadMultipleSelection__valueList">
          {this.mapSelectedValues(selectedValues)}
          {this.mapAvaliableValues(availableValues, selectedValues)}
        </div>
      </div>
    );
  }
}

TypeaheadMultipleSelection.defaultProps = {
  format: v => v
};
