import React from 'react';
import {Select, Popover, ProcessDefinitionSelection} from 'components';

import {Filter} from './filter';
import {reportLabelMap, extractProcessDefinitionName} from 'services';

import {TargetValueComparison} from './targetValue';

import {loadVariables} from './service';

import './ReportControlPanel.css';

export default class ReportControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.processDefinitionKey,
      variables: []
    };
  }

  componentDidMount() {
    this.loadProcessDefinitionName();
    this.loadVariables();
  }

  loadProcessDefinitionName = async () => {
    const {xml} = this.props.configuration;
    if (xml) {
      const processDefinitionName = await extractProcessDefinitionName(xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  loadVariables = async () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    if (processDefinitionKey && processDefinitionVersion) {
      this.setState({
        variables: await loadVariables(processDefinitionKey, processDefinitionVersion)
      });
    }
  };

  changeView = evt => {
    const viewKey = evt.target.value;
    const {groupBy, visualization} = this.getCurrentProps();
    const newCombination = reportLabelMap.getTheRightCombination(viewKey, groupBy, visualization);

    this.props.onChange({
      ...newCombination,
      configuration: {
        ...this.props.configuration,
        targetValue: {...this.props.configuration.targetValue, active: false}
      }
    });
  };
  changeGroup = evt => {
    const groupByKey = evt.target.value;
    const {view, visualization} = this.getCurrentProps();
    const newCombination = reportLabelMap.getTheRightCombination(view, groupByKey, visualization);

    this.props.onChange({
      ...newCombination,
      configuration: {
        ...this.props.configuration,
        targetValue: {...this.props.configuration.targetValue, active: false}
      }
    });
  };

  changeVisualization = evt => {
    this.props.onChange({
      visualization: evt.target.value,
      configuration: {
        ...this.props.configuration,
        targetValue: {...this.props.configuration.targetValue, active: false}
      }
    });
  };

  getCurrentProps = () => {
    return {
      view: reportLabelMap.objectToKey(this.props.view, 'view'),
      groupBy: reportLabelMap.objectToKey(this.props.groupBy, 'groupBy'),
      visualization: reportLabelMap.objectToKey(this.props.visualization, 'visualization')
    };
  };

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  componentDidUpdate(prevProps) {
    if (this.props.processDefinitionKey !== prevProps.processDefinitionKey) {
      this.loadProcessDefinitionName();
    }

    if (
      this.props.processDefinitionKey !== prevProps.processDefinitionKey ||
      this.props.processDefinitionVersion !== prevProps.processDefinitionVersion
    ) {
      this.loadVariables();
    }
  }

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    const processDefintionName = this.state.processDefinitionName
      ? this.state.processDefinitionName
      : processDefinitionKey;
    if (processDefintionName && processDefinitionVersion) {
      return `${processDefintionName} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process Definition';
    }
  };

  isEmpty = prop => {
    if (typeof prop === 'object') {
      let result = false;
      if (Object.keys(prop).length === 0) return true;
      Object.values(prop).forEach(str => {
        if (str !== null && this.isEmpty(str)) {
          result = true;
        }
      });
      return result;
    } else if (typeof prop === 'string') {
      return prop === '';
    }
  };

  isViewSelected = () => {
    return !this.isEmpty(this.props.view);
  };

  isGroupBySelected = () => {
    return !this.isEmpty(this.props.groupBy);
  };

  render() {
    return (
      <div className="ReportControlPanel">
        <ul className="ReportControlPanel__list">
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label
              htmlFor="ReportControlPanel__process-definition"
              className="ReportControlPanel__label"
            >
              Process definition
            </label>
            <Popover className="ReportControlPanel__popover" title={this.createTitle()}>
              <ProcessDefinitionSelection
                {...this.definitionConfig()}
                xml={this.props.configuration.xml}
                onChange={this.props.onChange}
                renderDiagram={true}
                enableAllVersionSelection={true}
              />
            </Popover>
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__view" className="ReportControlPanel__label">
              View
            </label>
            <Select
              className="ReportControlPanel__select"
              name="ReportControlPanel__view"
              value={reportLabelMap.objectToKey(this.props.view, reportLabelMap.view)}
              onChange={this.changeView}
            >
              {addSelectionOption()}
              {this.renderOptions('view')}
            </Select>
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__group-by" className="ReportControlPanel__label">
              Group by
            </label>
            <Select
              disabled={!this.isViewSelected()}
              className="ReportControlPanel__select"
              name="ReportControlPanel__group-by"
              value={reportLabelMap.objectToKey(this.props.groupBy, reportLabelMap.groupBy)}
              onChange={this.changeGroup}
            >
              {addSelectionOption()}
              {this.isViewSelected() && this.renderOptions('groupBy')}
            </Select>
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__visualize-as" className="ReportControlPanel__label">
              Visualize as
            </label>
            <Select
              disabled={!this.isGroupBySelected() || !this.isViewSelected()}
              className="ReportControlPanel__select"
              name="ReportControlPanel__visualize-as"
              value={this.props.visualization}
              onChange={this.changeVisualization}
            >
              {addSelectionOption()}
              {this.isGroupBySelected() &&
                this.isViewSelected() &&
                this.renderOptions('visualization')}
            </Select>
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--filter">
            <Filter
              data={this.props.filter}
              onChange={this.props.onChange}
              {...this.definitionConfig()}
              xml={this.props.configuration.xml}
            />
          </li>
          <li className="ReportControlPanel__item">
            <TargetValueComparison
              reportResult={this.props.reportResult}
              configuration={this.props.configuration}
              onChange={this.props.onChange}
            />
          </li>
        </ul>
      </div>
    );
  }

  renderOptions = type => {
    const options = reportLabelMap.getOptions(type);
    const {groupBy, view} = this.getCurrentProps();
    const enabled = reportLabelMap.getEnabledOptions(type, view, groupBy);

    if (type === 'groupBy') {
      options.push(
        ...this.state.variables.map(variable => {
          return {
            key: reportLabelMap.objectToKey({type: 'variable', value: variable}, type),
            label: `Variable - ${variable.name}`
          };
        })
      );
    }

    return options.map(({key, label}) => {
      const keyType = key.split('_')[0];
      return (
        <Select.Option disabled={enabled && !enabled.includes(keyType)} key={key} value={key}>
          {label}
        </Select.Option>
      );
    });
  };
}

function addSelectionOption() {
  return <Select.Option value="">Please select...</Select.Option>;
}
