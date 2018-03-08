import React from 'react';

import ControlPanel from './ControlPanel';
import {BPMNDiagram} from 'components';

import {loadProcessDefinitionXml, loadFrequencyData} from './service';
import DiagramBehavior from './DiagramBehavior';
import Statistics from './Statistics';

import './Analysis.css';

export default class Analysis extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      config: {
        processDefinitionKey: '',
        processDefinitionVersion: '',
        filter: []
      },
      data: null,
      hoveredControl: null,
      hoveredNode: null,
      gateway: null,
      endEvent: null,
      xml: null
    };
  }

  render() {
    const {xml, config, hoveredControl, hoveredNode, gateway, endEvent, data} = this.state;

    return (
      <div className="Analysis">
        <ControlPanel
          {...config}
          hoveredControl={hoveredControl}
          hoveredNode={hoveredNode}
          onChange={this.updateConfig}
          gateway={gateway}
          endEvent={endEvent}
          updateHover={this.updateHoveredControl}
          updateSelection={this.updateSelection}
          xml={xml}
        />
        <div className="Analysis__diagram">
          {xml && (
            <BPMNDiagram xml={xml}>
              <DiagramBehavior
                hoveredControl={hoveredControl}
                hoveredNode={hoveredNode}
                updateHover={this.updateHoveredNode}
                updateSelection={this.updateSelection}
                gateway={gateway}
                endEvent={endEvent}
                data={data}
              />
            </BPMNDiagram>
          )}
        </div>
        {gateway &&
          endEvent && <Statistics gateway={gateway} endEvent={endEvent} config={config} />}
      </div>
    );
  }

  async componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersion;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      prevConfig.processDefinitionVersion !== config.processDefinitionVersion;
    if (procDefConfigured && (procDefChanged || prevConfig.filter !== config.filter)) {
      this.setState({
        data: await loadFrequencyData(
          config.processDefinitionKey,
          config.processDefinitionVersion,
          config.filter
        )
      });
    }
  }

  updateHoveredControl = newField => {
    this.setState({hoveredControl: newField});
  };

  updateHoveredNode = newNode => {
    this.setState({hoveredNode: newNode});
  };

  updateSelection = (type, node) => {
    this.setState({[type]: node});
  };

  updateConfig = async updates => {
    const config = {
      ...this.state.config,
      ...updates
    };
    this.setState({config});

    if (updates.processDefinitionKey && updates.processDefinitionVersion) {
      this.setState({
        xml: await loadProcessDefinitionXml(
          config.processDefinitionKey,
          config.processDefinitionVersion
        ),
        gateway: null,
        endEvent: null
      });
    } else if (!config.processDefinitionKey || !config.processDefinitionVersion) {
      this.setState({
        xml: null,
        gateway: null,
        endEvent: null
      });
    }
  };
}
