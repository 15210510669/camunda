/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import update from 'immutability-helper';
import deepEqual from 'deep-equal';

import {evaluateReport} from 'services';
import {DashboardRenderer, EntityNameForm} from 'components';
import {t} from 'translation';
import {nowDirty, nowPristine} from 'saveGuard';

import {AddButton} from './AddButton';
import {DeleteButton} from './DeleteButton';
import DragOverlay from './DragOverlay';

export default class DashboardEdit extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      reports: props.initialReports,
      name: props.name
    };
  }

  contentContainer = React.createRef();

  mousePosition = {x: 0, y: 0};
  mouseTracker = evt => {
    this.mousePosition.x = evt.clientX;
    this.mousePosition.y = evt.clientY;
  };

  componentDidMount() {
    document.addEventListener('mousedown', this.setDraggedItem);
    document.addEventListener('mousemove', this.mouseTracker);
    document.addEventListener('mouseup', this.clearDraggedItem);
  }

  componentWillUnmount() {
    document.removeEventListener('mousedown', this.setDraggedItem);
    document.removeEventListener('mousemove', this.mouseTracker);
    document.removeEventListener('mouseup', this.clearDraggedItem);
  }

  draggedItem = null;
  setDraggedItem = evt => {
    this.draggedItem = evt.target.closest('.grid-entry');
    if (this.draggedItem) {
      // We need to prevent the browser scroll that occurs when resizing
      evt.preventDefault();

      // We need to give the library time to process the grab before
      // artificially generating mousemove events
      setTimeout(this.autoScroll);
    }
  };

  autoScroll = () => {
    if (this.draggedItem) {
      const container = this.contentContainer.current;
      const containerTop = container.offsetTop;
      const containerBottom = containerTop + container.offsetHeight;

      const deltaTop = this.mousePosition.y - containerTop;
      const deltaBottom = containerBottom - this.mousePosition.y;
      if (deltaTop < 30) {
        container.scrollTop -= (30 - deltaTop) / 5;
      } else if (deltaBottom < 30) {
        container.scrollTop += (30 - deltaBottom) / 5;
      }
      this.draggedItem.dispatchEvent(createEvent('mousemove', this.mousePosition));
      this.autoScrollHandle = requestAnimationFrame(this.autoScroll);
    }
  };

  clearDraggedItem = () => {
    this.draggedItem = null;

    // since we need to timeout the start of the autoscroll, we also need
    // to timeout the cancel to prevent a "cancel before start" bug
    setTimeout(() => {
      cancelAnimationFrame(this.autoScrollHandle);
    });
  };

  updateLayout = layout => {
    this.setState(({reports}) => {
      const newReports = reports.map((oldReport, idx) => {
        const newPosition = layout[idx];

        return {
          ...oldReport,
          position: {x: newPosition.x, y: newPosition.y},
          dimensions: {height: newPosition.h, width: newPosition.w}
        };
      });

      return {reports: newReports};
    });
  };

  updateName = ({target: {value}}) => {
    this.setState({name: value});
  };

  addReport = newReport => {
    this.setState({reports: update(this.state.reports, {$push: [newReport]})}, () => {
      const node = document.querySelector('.react-grid-layout').lastChild;
      const nodePos = node.getBoundingClientRect();

      // dispatch a mouse event to automatically grab the new report for positioning
      node.dispatchEvent(
        createEvent('mousedown', {
          x: nodePos.x + nodePos.width / 2,
          y: nodePos.y + nodePos.height / 2
        })
      );

      // prevent the next mousedown event (it confuses the grid library)
      node.addEventListener(
        'mousedown',
        evt => {
          evt.preventDefault();
          evt.stopPropagation();
        },
        {capture: true, once: true}
      );

      window.setTimeout(() => {
        node.dispatchEvent(createEvent('mousemove', this.mousePosition));
        node.dispatchEvent(createEvent('mousemove', this.mousePosition));
      });
    });
  };

  deleteReport = ({report: reportToRemove}) => {
    this.setState({
      reports: this.state.reports.filter(report => report !== reportToRemove)
    });
  };

  componentDidUpdate() {
    if (
      deepEqual(this.state.reports, this.props.initialReports) &&
      this.state.name === this.props.name
    ) {
      nowPristine();
    } else {
      nowDirty(t('dashboard.label'), this.save);
    }
  }

  save = async () => {
    const {name, reports} = this.state;

    nowPristine();
    await this.props.saveChanges(name, reports);
  };

  render() {
    const {lastModifier, lastModified, isNew} = this.props;

    const {reports, name} = this.state;

    return (
      <div className="DashboardEdit">
        <div className="header">
          <EntityNameForm
            name={name}
            lastModified={lastModified}
            lastModifier={lastModifier}
            isNew={isNew}
            entity="Dashboard"
            onChange={this.updateName}
            onSave={this.save}
            onCancel={nowPristine}
          >
            <AddButton addReport={this.addReport} />
          </EntityNameForm>
          <div className="subHead">
            <div className="metadata">
              {t('common.entity.modified')} {moment(lastModified).format('lll')}{' '}
              {t('common.entity.by')} {lastModifier}
            </div>
          </div>
        </div>
        <div className="content" ref={this.contentContainer}>
          <DashboardRenderer
            disableReportInteractions
            reports={reports}
            loadReport={evaluateReport}
            addons={[
              <DragOverlay key="DragOverlay" />,
              <DeleteButton key="DeleteButton" deleteReport={this.deleteReport} />
            ]}
            onChange={this.updateLayout}
          />
        </div>
      </div>
    );
  }
}

function createEvent(type, position) {
  return new MouseEvent(type, {
    view: window,
    bubbles: true,
    cancelable: true,
    clientX: position.x,
    clientY: position.y
  });
}
