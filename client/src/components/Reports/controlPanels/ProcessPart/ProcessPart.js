/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  Button,
  Modal,
  BPMNDiagram,
  ClickBehavior,
  PartHighlight,
  ActionItem,
  MessageBox
} from 'components';

import './ProcessPart.scss';
import {t} from 'translation';

export default class ProcessPart extends React.Component {
  state = {
    modalOpen: false,
    start: null,
    end: null,
    hasPath: true,
    hoveredNode: null
  };

  render() {
    return (
      <React.Fragment>
        {this.renderButton()}
        {this.renderPart()}
        {this.renderModal()}
      </React.Fragment>
    );
  }

  renderButton() {
    if (!this.props.processPart) {
      return <Button onClick={this.openModal}>{t('report.processPart.label')}</Button>;
    }
  }

  renderFlowNodeName = id => {
    return this.props.flowNodeNames ? this.props.flowNodeNames[id] || id : id;
  };

  renderPart() {
    if (this.props.processPart) {
      return (
        <div onClick={this.openModal} className="ProcessPart__current">
          <ActionItem
            onClick={evt => {
              evt.stopPropagation();
              this.props.update(null);
            }}
          >
            {t('report.processPart.description')}{' '}
            <span className="highlighted">
              {this.renderFlowNodeName(this.props.processPart.start)}
            </span>
            <span> {t('common.and')} </span>
            <span className="highlighted">
              {this.renderFlowNodeName(this.props.processPart.end)}
            </span>
          </ActionItem>
        </div>
      );
    }
  }

  setHasPath = hasPath => {
    if (this.state.hasPath !== hasPath) {
      // this is called during render of PartHighlight. We cannot update state during a render, so we do it later
      window.setTimeout(() => this.setState({hasPath}));
    }
  };

  renderModal() {
    const {start, end, hasPath, modalOpen, hoveredNode} = this.state;

    const selection = [start, end].filter(v => v);
    return (
      <Modal
        open={modalOpen}
        onClose={this.closeModal}
        onConfirm={this.isValid() ? this.applyPart : undefined}
        size="max"
        className="ProcessPartModal"
      >
        <Modal.Header>{t('report.processPart.title')}</Modal.Header>
        <Modal.Content>
          <span>
            {t('report.processPart.description')}{' '}
            <ActionItem
              disabled={!start}
              highlighted={hoveredNode && (start ? hoveredNode.id === start.id : true)}
              onClick={() => this.setState({start: null, hasPath: true})}
            >
              {start ? start.name || start.id : t('report.processPart.selectStart')}
            </ActionItem>
            <span> {t('common.and')} </span>
            <ActionItem
              highlighted={
                hoveredNode &&
                start &&
                (end ? hoveredNode.id === end.id : hoveredNode.id !== start.id)
              }
              disabled={!end}
              onClick={() => this.setState({end: null, hasPath: true})}
            >
              {end ? end.name || end.id : t('report.processPart.selectEnd')}
            </ActionItem>
          </span>
          {start && end && !hasPath && (
            <MessageBox type="warning">{t('report.processPart.noPathWarning')}</MessageBox>
          )}
          <div className="diagram-container">
            <BPMNDiagram xml={this.props.xml}>
              <ClickBehavior
                setSelectedNodes={this.setSelectedNodes}
                onClick={this.selectNode}
                onHover={hoveredNode => this.setState({hoveredNode})}
                selectedNodes={selection}
              />
              <PartHighlight nodes={selection} setHasPath={this.setHasPath} />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.closeModal}>{t('common.cancel')}</Button>
          <Button
            variant="primary"
            color="blue"
            onClick={this.applyPart}
            disabled={!this.isValid()}
          >
            {t('common.apply')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  openModal = () => this.setState({modalOpen: true, ...this.props.processPart});
  closeModal = () => this.setState({modalOpen: false, start: null, end: null, hasPath: true});

  setSelectedNodes = ([start, end]) => {
    this.setState({start, end});
  };

  selectNode = node => {
    if (this.state.start === node) {
      return this.setState({start: null, hasPath: true});
    }
    if (this.state.end === node) {
      return this.setState({end: null, hasPath: true});
    }
    if (!this.state.start) {
      return this.setState({start: node});
    } else {
      return this.setState({end: node});
    }
  };

  applyPart = () => {
    this.props.update({start: this.state.start.id, end: this.state.end.id});
    this.closeModal();
  };

  isValid = () => this.state.start && this.state.end;
}
