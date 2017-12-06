import React from 'react';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter, NodeFilter} from './modals';

import FilterList from './FilterList';
import './Filter.css';

export default class Filter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      newFilterType: null
    }
  }

  openNewFilterModal = type => evt => {
    this.setState({newFilterType: type});
  }

  closeModal = () => {
    this.setState({newFilterType: null});
  }

  getFilterModal = () => {
    switch(this.state.newFilterType) {
      case 'date': return DateFilter;
      case 'variable': return VariableFilter;
      case 'node': return NodeFilter;
      default: return () => null;
    }
  }

  addFilter = (...newFilters) => {
    this.props.onChange('filter', [...this.props.data, ...newFilters]);
    this.closeModal();
  }

  deleteFilter = (...oldFilters) => {
    this.props.onChange('filter', [...this.props.data.filter(filter => !oldFilters.includes(filter))]);
  }

  processDefinitionIsNotSelected = () => {
    return !this.props.processDefinitionId || 
    this.props.processDefinitionId === '';
  }

  render() {
    const FilterModal = this.getFilterModal();

    return (<div className='Filter'>
      <label htmlFor='ControlPanel__filters' className='visually-hidden'>Filters</label>
      <FilterList data={this.props.data} deleteFilter={this.deleteFilter} />
      <Dropdown label='Add Filter' id='ControlPanel__filters' className='Filter__dropdown' >
        <Dropdown.Option onClick={this.openNewFilterModal('date')}>Start Date</Dropdown.Option>
        <Dropdown.Option disabled={this.processDefinitionIsNotSelected()} onClick={this.openNewFilterModal('variable')}>Variable</Dropdown.Option>
        <Dropdown.Option disabled={this.processDefinitionIsNotSelected()} onClick={this.openNewFilterModal('node')}>Flow Node</Dropdown.Option>
      </Dropdown>
      <FilterModal addFilter={this.addFilter} close={this.closeModal} processDefinitionId={this.props.processDefinitionId} />
    </div>);
  }

  
}
