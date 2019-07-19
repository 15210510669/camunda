/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter} from './modals';

import FilterList from './FilterList';
import './Filter.scss';

import {loadDecisionValues, filterIncompatibleExistingFilters} from './service';

export default class DecisionFilter extends React.Component {
  state = {
    newFilterType: null,
    editFilter: null
  };

  openNewFilterModal = type => () => {
    this.setState({newFilterType: type});
  };

  openEditFilterModal = filter => () => {
    if (filter.type === 'inputVariable' || filter.type === 'outputVariable') {
      const variable = this.props.variables[filter.type].find(({id}) => filter.data.name === id);

      this.setState({
        editFilter: update(filter, {
          data: {
            id: {$set: variable.id},
            name: {$set: variable.name}
          }
        })
      });
    } else {
      this.setState({editFilter: filter});
    }
  };

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: null});
  };

  getFilterModal = type => {
    switch (type) {
      case 'evaluationDateTime':
        return DateFilter;
      case 'inputVariable':
      case 'outputVariable':
        return VariableFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = type => {
    if (type === 'inputVariable' || type === 'outputVariable') {
      return {
        getVariables: () => this.props.variables[type],
        getValues: async (id, varType, numResults, valueFilter) =>
          await loadDecisionValues(
            type,
            this.props.decisionDefinitionKey,
            this.props.decisionDefinitionVersions,
            id,
            varType,
            0,
            numResults,
            valueFilter
          )
      };
    }
  };

  editFilter = newFilter => {
    const filters = this.props.data;

    let index;
    if (newFilter.type === 'inputVariable' || newFilter.type === 'outputVariable') {
      index = filters.indexOf(filters.find(v => this.state.editFilter.data.id === v.data.name));
    } else {
      index = filters.indexOf(filters.find(v => this.state.editFilter.data === v.data));
    }

    this.props.onChange({filter: {[index]: {$set: newFilter}}}, true);
    this.closeModal();
  };

  addFilter = newFilter => {
    let filters = this.props.data;
    filters = filterIncompatibleExistingFilters(filters, newFilter.type, ['evaluationDateTime']);

    this.props.onChange({filter: {$set: [...filters, newFilter]}}, true);
    this.closeModal();
  };

  deleteFilter = oldFilter => {
    this.props.onChange(
      {
        filter: {$set: this.props.data.filter(filter => oldFilter !== filter)}
      },
      true
    );
  };

  definitionIsNotSelected = () => {
    return (
      !this.props.decisionDefinitionKey ||
      this.props.decisionDefinitionKey === '' ||
      !this.props.decisionDefinitionVersions ||
      this.props.decisionDefinitionVersions.length === 0
    );
  };

  render() {
    const FilterModal = this.getFilterModal(this.state.newFilterType);
    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter.type : null
    );

    return (
      <div className="Filter">
        <FilterList
          openEditFilterModal={this.openEditFilterModal}
          data={this.props.data}
          deleteFilter={this.deleteFilter}
          variables={this.props.variables}
        />
        <Dropdown label="Add Filter" id="ControlPanel__filters" className="Filter__dropdown">
          <Dropdown.Option onClick={this.openNewFilterModal('evaluationDateTime')}>
            Evaluation Date Time
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.definitionIsNotSelected()}
            onClick={this.openNewFilterModal('inputVariable')}
          >
            Input Variable
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.definitionIsNotSelected()}
            onClick={this.openNewFilterModal('outputVariable')}
          >
            Output Variable
          </Dropdown.Option>
        </Dropdown>
        {this.props.instanceCount !== undefined && (
          <span className="instanceCount">
            {this.props.instanceCount} evaluation{this.props.instanceCount !== 1 && 's'} in current
            filter
          </span>
        )}
        <FilterModal
          addFilter={this.addFilter}
          close={this.closeModal}
          filterType={this.state.newFilterType}
          config={this.getFilterConfig(this.state.newFilterType)}
        />
        <EditFilterModal
          addFilter={this.editFilter}
          filterData={this.state.editFilter}
          close={this.closeModal}
          filterType={this.state.editFilter && this.state.editFilter.type}
          config={this.getFilterConfig(this.state.editFilter && this.state.editFilter.type)}
        />
      </div>
    );
  }
}
