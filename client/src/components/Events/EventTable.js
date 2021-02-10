/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import deepEqual from 'deep-equal';

import {Table, LoadingIndicator, Input, Select, Switch, Icon, Button} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';

import {loadEvents, isNonTimerEvent} from './service';
import EventsSources from './EventsSources';

import './EventTable.scss';

const debounceRequest = debouncePromise();

const asMapping = ({group, source, eventName, eventLabel}) => ({
  group,
  source,
  eventName,
  eventLabel,
});

export default withErrorHandling(
  class EventTable extends React.Component {
    container = React.createRef();

    state = {
      events: null,
      searchQuery: '',
      showSuggested: true,
      sorting: {
        by: 'group',
        order: 'asc',
      },
    };

    async componentDidMount() {
      this.setState({events: await this.loadEvents(this.state.searchQuery)});
    }

    loadEvents = (searchQuery) => {
      return new Promise((resolve) => {
        const {selection, xml, mappings, eventSources} = this.props;

        this.setState({events: null});

        let payload = {eventSources};
        if (
          !this.camundaSourcesAdded() &&
          this.state.showSuggested &&
          this.getNumberOfPotentialMappings(selection)
        ) {
          payload = {
            targetFlowNodeId: selection.id,
            xml,
            mappings,
            eventSources,
          };
        }

        this.props.mightFail(
          loadEvents(payload, searchQuery, this.state.sorting),
          resolve,
          showError
        );
      });
    };

    getNumberOfPotentialMappings = (node) => {
      if (!node) {
        return 0;
      }
      if (node.$instanceOf('bpmn:Task')) {
        return 2;
      } else if (node.$instanceOf('bpmn:Event')) {
        return 1;
      } else {
        return 0;
      }
    };

    mappedAs = (event) => {
      const mappings = Object.values(this.props.mappings);

      for (let i = 0; i < mappings.length; i++) {
        if (mappings[i]) {
          const {start, end} = mappings[i];
          const eventAsMapping = asMapping(event);

          if (deepEqual(start, eventAsMapping)) {
            return 'start';
          }
          if (deepEqual(end, eventAsMapping)) {
            return 'end';
          }
        }
      }
    };

    searchFor = async (searchQuery) => {
      this.setState({searchQuery, events: null});
      const events = await debounceRequest(this.loadEvents, 300, searchQuery);
      this.setState({events});
    };

    async componentDidUpdate(prevProps, prevState) {
      this.updateTableAfterSelectionChange(prevProps);
      if (prevState.events === null && this.state.events !== null) {
        this.scrollToSelectedElement();
      }

      if (
        prevProps.eventSources !== this.props.eventSources ||
        prevState.sorting !== this.state.sorting
      ) {
        this.setState({events: await this.loadEvents(this.state.searchQuery)});
      }
    }

    updateTableAfterSelectionChange = async (prevProps) => {
      const {selection} = this.props;
      const {showSuggested, searchQuery} = this.state;

      const prevSelection = prevProps.selection;

      const selectionMade = !prevSelection && selection;
      const selectionChanged = selection && prevSelection && prevSelection.id !== selection.id;
      const selectionCleared = prevSelection && !selection;

      if (selectionMade || selectionChanged || selectionCleared) {
        if (!this.camundaSourcesAdded() && showSuggested) {
          this.setState({events: await this.loadEvents(searchQuery)});
        } else {
          this.scrollToSelectedElement();
        }
      }
    };

    scrollToSelectedElement = () => {
      const {selection, mappings} = this.props;
      const {start, end} = (selection && mappings[selection.id]) || {};
      const event = start || end;
      if (event) {
        const mappedElement = this.container.current?.querySelector('.' + event.eventName);
        if (mappedElement) {
          mappedElement.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
      }
    };

    inShownSources = (event) => {
      const shownSources = this.props.eventSources.filter((src) => !src.hidden);

      return shownSources.some(({configuration, type}) => {
        if (type === 'external') {
          return event.source !== 'camunda';
        } else {
          return event.group === configuration.processDefinitionKey;
        }
      });
    };

    camundaSourcesAdded = () =>
      this.props.eventSources.filter((src) => src.type !== 'external').length > 0;

    render() {
      const {events, searchQuery, showSuggested, collapsed, sorting} = this.state;
      const {selection, onMappingChange, mappings, eventSources} = this.props;

      const {start, end} = (selection && mappings[selection.id]) || {};
      const numberOfMappings = !!start + !!end;
      const numberOfPotentialMappings = this.getNumberOfPotentialMappings(selection);
      const allMapped = numberOfPotentialMappings <= numberOfMappings;
      const externalEvents = eventSources.some((src) => src.type === 'external');

      return (
        <div className="EventTable" ref={this.container}>
          <div className="header">
            <b>{t('events.list')}</b>
            <Switch
              disabled={this.camundaSourcesAdded()}
              checked={!this.camundaSourcesAdded() && showSuggested}
              onChange={({target: {checked}}) =>
                this.setState({showSuggested: checked}, async () =>
                  this.setState({events: await this.loadEvents(searchQuery)})
                )
              }
              title={this.camundaSourcesAdded() ? t('events.table.noSuggestionsMessage') : ''}
              label={t('events.table.showSuggestions')}
            />
            <EventsSources sources={eventSources} onChange={this.props.onSourcesChange} />
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                required
                type="text"
                className="searchInput"
                placeholder={t('home.search.name')}
                value={searchQuery}
                onChange={({target: {value}}) => this.searchFor(value)}
                onClear={() => this.searchFor('')}
              />
            </div>
            <Button
              onClick={() => this.setState({collapsed: !collapsed})}
              className="collapseButton"
            >
              <Icon type={collapsed ? 'expand' : 'collapse'} />
            </Button>
          </div>
          <Table
            className={classnames({collapsed})}
            head={[
              {label: 'checked', id: 'checked', sortable: false},
              {label: t('events.table.mapping'), id: 'mapping', sortable: false},
              {label: t('events.table.name'), id: 'eventName'},
              {label: t('events.table.group'), id: 'group'},
              {label: t('events.table.source'), id: 'source'},
              {label: t('events.table.count'), id: 'count'},
            ]}
            body={
              events
                ? events.filter(this.inShownSources).map((event) => {
                    const {group, source, eventLabel, eventName, count, suggested} = event;
                    const mappedAs = this.mappedAs(event);
                    const eventAsMapping = asMapping(event);
                    const mappedToSelection =
                      deepEqual(start, asMapping(event)) || deepEqual(end, asMapping(event));
                    const disabled = !selection || (!mappedToSelection && (allMapped || mappedAs));
                    const showDropdown = mappedAs && !disabled && !isNonTimerEvent(selection);

                    return {
                      content: [
                        <Input
                          type="checkbox"
                          checked={!!mappedAs}
                          disabled={disabled}
                          onChange={({target: {checked}}) =>
                            onMappingChange(eventAsMapping, checked)
                          }
                        />,
                        showDropdown ? (
                          <Select
                            value={mappedAs}
                            onChange={(value) =>
                              mappedAs !== value && onMappingChange(eventAsMapping, true, value)
                            }
                          >
                            <Select.Option
                              value="end"
                              disabled={mappedAs !== 'end' && numberOfMappings === 2}
                            >
                              {t('events.table.end')}
                            </Select.Option>
                            <Select.Option
                              value="start"
                              disabled={mappedAs !== 'start' && numberOfMappings === 2}
                            >
                              {t('events.table.start')}
                            </Select.Option>
                          </Select>
                        ) : (
                          <span className={classnames({disabled})}>
                            {mappedAs ? t(`events.table.${mappedAs}`) : '--'}
                          </span>
                        ),
                        eventLabel || eventName,
                        group,
                        source,
                        count ?? '--',
                      ],
                      props: {
                        className: classnames(eventName, {
                          disabled,
                          mapped: mappedAs,
                          suggested,
                        }),
                        onClick: (evt) => {
                          const type = evt.target.getAttribute('type');
                          if (mappedAs) {
                            if (type !== 'checkbox' && !evt.target.closest('.Dropdown')) {
                              this.props.onSelectEvent(event);
                            } else if (type === 'checkbox') {
                              this.props.onSelectEvent(null);
                            }
                          }
                        },
                      },
                    };
                  })
                : []
            }
            disablePagination
            noData={
              <>
                {!events && <LoadingIndicator />}
                {events && searchQuery && t('events.table.noResults')}
                {events && !!events.length && !searchQuery && t('events.table.allMapped')}
                {events &&
                  !events.length &&
                  !searchQuery &&
                  !externalEvents &&
                  t('events.sources.empty')}
              </>
            }
            noHighlight
            sorting={sorting}
            updateSorting={(by, order) => this.setState({sorting: {by, order}})}
          />
        </div>
      );
    }
  }
);
