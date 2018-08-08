import React from 'react';
import classnames from 'classnames';
import {Modal, ButtonGroup, Button, BPMNDiagram} from 'components';

import ClickBehavior from './ClickBehavior';

import './NodeFilter.css';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      selectedNodes: this.props.filterData ? this.props.filterData.data.values : [],
      operator: this.props.filterData ? this.props.filterData.data.operator : 'in'
    };
  }

  toggleNode = toggledNode => {
    if (this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter(node => node !== toggledNode)
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode])
      });
    }
  };

  createFilter = () => {
    const values = this.state.selectedNodes.map(node => node.id);

    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {
        operator: this.state.operator,
        values
      }
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  createOperator = name => {
    return <span className="NodeFilter__preview-item-operator"> {name} </span>;
  };

  createPreviewList = () => {
    const previewList = [];

    this.state.selectedNodes.forEach((selectedNode, idx) => {
      previewList.push(
        <li key={idx} className="NodeFilter__preview-item">
          <span key={idx}>
            {' '}
            <span className="NodeFilter__preview-item-value">
              {selectedNode.name || selectedNode.id}
            </span>{' '}
            {idx < this.state.selectedNodes.length - 1 &&
              this.createOperator(this.state.operator === 'in' ? 'or' : 'nor')}
          </span>
        </li>
      );
    });
    return (
      <ul className="NodeFilter__preview">
        <span className="NodeFilter__preview-introduction">
          This is the filter you are about to create:{' '}
        </span>{' '}
        <span className="NodeFilter__parameter-name">Executed Flow Node</span>
        {this.createOperator(
          this.state.operator === 'in'
            ? 'is'
            : this.state.selectedNodes.length > 1 ? 'is neither' : 'is not'
        )}
        {previewList}
      </ul>
    );
  };

  setSelectedNodes = nodes => {
    this.setState({
      selectedNodes: nodes
    });
  };

  render() {
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={this.isNodeSelected() ? this.createFilter : undefined}
        className="NodeFilter__modal"
        size="max"
      >
        <Modal.Header>Add Flow Node Filter</Modal.Header>
        <Modal.Content className="NodeFilter__modal-content">
          {this.createPreviewList()}
          <div className="VariableFilter__buttonRow">
            <ButtonGroup>
              <Button
                className={classnames({'is-active': this.state.operator === 'in'})}
                onClick={() => this.setState({operator: 'in'})}
              >
                was executed
              </Button>
              <Button
                className={classnames({
                  'is-active': this.state.operator === 'not in'
                })}
                onClick={() => this.setState({operator: 'not in'})}
              >
                was not executed
              </Button>
            </ButtonGroup>
          </div>
          {this.props.xml && (
            <div className="NodeFilter__diagram-container">
              <BPMNDiagram xml={this.props.xml}>
                <ClickBehavior
                  setSelectedNodes={this.setSelectedNodes}
                  onClick={this.toggleNode}
                  selectedNodes={this.state.selectedNodes}
                />
              </BPMNDiagram>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
