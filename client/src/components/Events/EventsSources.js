/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';

import {Dropdown, Button, Icon, Deleter} from 'components';
import EventsSourceModal from './EventsSourceModal';
import VisibleEventsModal from './VisibleEventsModal';
import {t} from 'translation';

import './EventsSources.scss';

export default class EventSources extends React.Component {
  state = {
    editing: null,
    deleting: null,
    editingScope: null,
  };

  openAddSourceModal = () => this.setState({editing: {}});
  openEditSourceModal = (editing) => this.setState({editing});
  closeSourceModal = () => this.setState({editing: null});
  openEditScopeModal = (editingScope) => this.setState({editingScope});
  closeEditScopeModal = () => this.setState({editingScope: null});

  removeSource = (target) => {
    this.props.onChange(
      this.props.sources.filter((src) => src !== target),
      true
    );
  };

  toggleSource = (targetSource) => {
    const sourceIndex = this.props.sources.indexOf(targetSource);
    this.props.onChange(update(this.props.sources, {[sourceIndex]: {$toggle: ['hidden']}}), false);
  };

  updateSourceScope = (newScope) => {
    const sourceIndex = this.props.sources.findIndex(
      ({processDefinitionKey}) =>
        processDefinitionKey === this.state.editingScope.processDefinitionKey
    );
    const updateSources = update(this.props.sources, {
      [sourceIndex]: {eventScope: {$set: newScope}},
    });
    this.props.onChange(updateSources, true);
    this.closeEditScopeModal();
  };

  render() {
    const {editing, deleting, editingScope} = this.state;
    const {sources} = this.props;

    return (
      <div className="EventsSources">
        <div className="sourcesList">
          {sources.map((source) => {
            return (
              <Dropdown
                className={classnames({isActive: !source.hidden})}
                {...getDropdownProps(source)}
              >
                <Dropdown.Option onClick={() => this.toggleSource(source)}>
                  {source.hidden ? t('events.sources.show') : t('events.sources.hide')}
                </Dropdown.Option>
                {source.type !== 'external' && (
                  <>
                    <Dropdown.Option onClick={() => this.openEditSourceModal(source)}>
                      {t('events.sources.editSource')}
                    </Dropdown.Option>
                    <Dropdown.Option onClick={() => this.openEditScopeModal(source)}>
                      {t('events.sources.editScope')}
                    </Dropdown.Option>
                  </>
                )}
                <Dropdown.Option onClick={() => this.setState({deleting: source})}>
                  {t('events.sources.remove')}
                </Dropdown.Option>
              </Dropdown>
            );
          })}
        </div>
        <Button className="addProcess" onClick={this.openAddSourceModal}>
          <Icon type="plus" size="14" />
          {t('events.sources.add')}
        </Button>
        <Deleter
          type="processEvents"
          deleteText={t('common.remove')}
          entity={deleting}
          deleteEntity={this.removeSource}
          onClose={() => this.setState({deleting: null})}
          descriptionText={t('events.sources.deleteWarning')}
        />
        {editing && (
          <EventsSourceModal
            initialSource={editing}
            existingSources={sources}
            onConfirm={(sources) => {
              const isEditing = Object.keys(editing).length > 0;
              this.props.onChange(sources, isEditing);
              this.closeSourceModal();
            }}
            onClose={this.closeSourceModal}
          />
        )}
        {editingScope && (
          <VisibleEventsModal
            initialScope={editingScope.eventScope}
            onConfirm={this.updateSourceScope}
            onClose={this.closeEditScopeModal}
          />
        )}
      </div>
    );
  }
}

function getDropdownProps({processDefinitionKey, processDefinitionName, type}) {
  if (type === 'external') {
    return {
      label: t('events.sources.externalEvents'),
      key: 'externalEvents',
    };
  } else {
    return {
      label: processDefinitionName || processDefinitionKey,
      key: processDefinitionKey,
    };
  }
}
