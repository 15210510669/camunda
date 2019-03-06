import React from 'react';
import {Popover, DefinitionSelection, Labeled} from 'components';
import {formatters} from 'services';

import {Configuration} from './Configuration';
import ReportDropdown from './ReportDropdown';

import {DecisionFilter} from './filter';

import {reportConfig, loadDecisionDefinitionXml, extractDefinitionName} from 'services';
const {decision: decisionConfig} = reportConfig;

export default class DecisionControlPanel extends React.Component {
  state = {};

  static getDerivedStateFromProps({
    report: {
      data: {
        configuration: {xml},
        decisionDefinitionKey
      }
    }
  }) {
    if (xml) {
      const definitions = new DOMParser()
        .parseFromString(xml, 'text/xml')
        .querySelector(`decision[id="${decisionDefinitionKey}"]`);

      return {
        variables: {
          inputVariable: Array.from(definitions.querySelectorAll('input')).map(node => ({
            id: node.getAttribute('id'),
            name: node.getAttribute('label'),
            type: node.querySelector('inputExpression').getAttribute('typeRef')
          })),
          outputVariable: Array.from(definitions.querySelectorAll('output')).map(node => ({
            id: node.getAttribute('id'),
            name: node.getAttribute('label'),
            type: node.getAttribute('typeRef')
          }))
        }
      };
    }
    return null;
  }

  createTitle = () => {
    const {
      decisionDefinitionKey,
      decisionDefinitionVersion,
      configuration: {xml}
    } = this.props.report.data;
    if (xml) {
      return `${extractDefinitionName(decisionDefinitionKey, xml)} : ${decisionDefinitionVersion}`;
    } else {
      return 'Select Decision';
    }
  };

  changeDefinition = async (key, version) => {
    const {groupBy, filter} = this.props.report.data;

    const change = {
      decisionDefinitionKey: {$set: key},
      decisionDefinitionVersion: {$set: version},
      configuration: {
        excludedColumns: {$set: []},
        columnOrder: {
          $set: {
            inputVariables: [],
            instanceProps: [],
            outputVariables: [],
            variables: []
          }
        },
        xml: {$set: key && version ? await loadDecisionDefinitionXml(key, version) : null}
      },
      filter: {
        $set: filter.filter(({type}) => type !== 'inputVariable' && type !== 'outputVariable')
      }
    };

    if (groupBy && (groupBy.type === 'inputVariable' || groupBy.type === 'outputVariable')) {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    this.props.updateReport(change, true);
  };

  updateReport = (type, newValue) => {
    this.props.updateReport(
      decisionConfig.update(type, newValue, this.props),
      type !== 'visualization'
    );
  };

  render() {
    const {data, decisionInstanceCount} = this.props.report;
    const {
      decisionDefinitionKey,
      decisionDefinitionVersion,
      filter,
      visualization,
      configuration: {xml}
    } = data;

    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <ul>
          <li className="select">
            <Labeled label="Decision definition">
              <Popover className="processDefinitionPopover" title={this.createTitle()}>
                <DefinitionSelection
                  type="decision"
                  definitionKey={decisionDefinitionKey}
                  definitionVersion={decisionDefinitionVersion}
                  onChange={this.changeDefinition}
                  enableAllVersionSelection
                />
              </Popover>
            </Labeled>
          </li>
          {['view', 'groupBy', 'visualization'].map((field, idx, fields) => {
            const previous = fields
              .filter((prev, prevIdx) => prevIdx < idx)
              .map(prev => data[prev]);

            return (
              <li className="select" key={field}>
                <Labeled label={formatters.convertCamelToSpaces(field)}>
                  <ReportDropdown
                    type="decision"
                    field={field}
                    value={data[field]}
                    xml={xml}
                    variables={this.state.variables}
                    previous={previous}
                    disabled={
                      !decisionDefinitionKey ||
                      !decisionDefinitionVersion ||
                      previous.some(entry => !entry)
                    }
                    onChange={newValue => this.updateReport(field, newValue)}
                  />
                </Labeled>
              </li>
            );
          })}
          <li className="filter">
            <DecisionFilter
              data={filter}
              onChange={this.props.updateReport}
              instanceCount={decisionInstanceCount}
              decisionDefinitionKey={decisionDefinitionKey}
              decisionDefinitionVersion={decisionDefinitionVersion}
              variables={this.state.variables}
            />
          </li>
          <Configuration
            type={visualization}
            onChange={this.props.updateReport}
            report={this.props.report}
          />
        </ul>
      </div>
    );
  }
}
