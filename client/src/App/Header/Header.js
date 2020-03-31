/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';
import {withData} from 'modules/DataManager';
import {withCountStore} from 'modules/contexts/CountContext';
import {withRouter} from 'react-router';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import {wrapWithContexts} from 'modules/contexts/contextHelpers';
import withSharedState from 'modules/components/withSharedState';
import {getFilterQueryString, parseQueryString} from 'modules/utils/filter';
import {
  FILTER_SELECTION,
  BADGE_TYPE,
  LOADING_STATE,
  DEFAULT_FILTER,
} from 'modules/constants';

import {isEqual} from 'lodash';
import {labels, createTitle, PATHNAME} from './constants';

import User from './User';
import InstanceDetail from './InstanceDetail';
import {NavElement, BrandNavElement, LinkElement} from './NavElements';
import * as Styled from './styled.js';

class Header extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
    countStore: PropTypes.shape({
      isLoaded: PropTypes.bool,
      running: PropTypes.number,
      active: PropTypes.number,
      filterCount: PropTypes.number,
      withIncidents: PropTypes.number,
    }),
    location: PropTypes.object,
    isFiltersCollapsed: PropTypes.bool.isRequired,
    expandFilters: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func,
  };

  constructor(props) {
    super(props);

    this.subscriptions = {
      LOAD_INSTANCE: ({state, response}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({
            instance: null,
          });
        }
        if (state === LOADING_STATE.LOADED) {
          this.setState({
            instance: response,
          });
        }
      },
      CONSTANT_REFRESH: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          const {LOAD_INSTANCE} = response;

          this.setState({
            instance: LOAD_INSTANCE,
          });
        }
      },
    };
    this.state = {
      forceRedirect: false,
      user: {},
      instance: null,
      filter: null,
      isLoaded: false,
    };
  }

  componentDidMount = () => {
    const {
      countStore: {isLoaded},
      dataManager,
    } = this.props;

    dataManager.subscribe(this.subscriptions);

    if (isLoaded) {
      this.setState({isLoaded});
    }
  };

  componentDidUpdate = (prevProps, prevState) => {
    const {
      location,
      countStore: {isLoaded},
    } = this.props;

    if (prevState.isLoaded !== isLoaded) {
      this.setState({isLoaded});
    }

    // Instances View: Set filter count from URL
    if (
      this.currentView().isInstances() &&
      prevProps.location.search !== location.search
    ) {
      const filterFromURL = parseQueryString(location.search).filter;
      this.setState({filter: filterFromURL});
    } else if (!this.state.filter) {
      this.setState({filter: DEFAULT_FILTER});
    }
  };

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  currentView() {
    const {DASHBOARD, INSTANCES, INSTANCE} = PATHNAME;
    const {pathname} = this.props.location;

    return {
      isDashboard: () => pathname === DASHBOARD,
      isInstances: () => pathname === INSTANCES,
      isInstance: () => pathname.includes(INSTANCE),
    };
  }

  handleRedirect = () => {
    this.setState({forceRedirect: true});
  };

  getFilterResetProps = (type) => {
    const filters = {
      instances: DEFAULT_FILTER,
      filters: this.state.filter || {},
      incidents: {incidents: true},
    };
    return {
      onClick: (e) => {
        e.preventDefault();
        this.props.expandFilters();
        this.props.onFilterReset(filters[type]);
      },
      to: ' ',
    };
  };

  getListLinksProps = (type) => {
    if (this.props.onFilterReset) {
      return this.getFilterResetProps(type);
    }

    const queryStrings = {
      filters: this.state.filter ? getFilterQueryString(this.state.filter) : '',
      instances: getFilterQueryString(FILTER_SELECTION.running),
      incidents: getFilterQueryString({incidents: true}),
    };

    return {
      to: `/instances${queryStrings[type]}`,
      onClick: this.props.expandFilters,
    };
  };

  selectActiveCondition(type) {
    const currentView = this.currentView();
    const {filter} = this.state;

    // Is 'running instances' or 'incidents badge' active;
    if (type === 'instances' || type === 'incidents') {
      const isRunningInstanceFilter = isEqual(filter, FILTER_SELECTION.running);
      const conditions = {
        instances: currentView.isInstances() && isRunningInstanceFilter,
        incidents:
          currentView.isInstances() &&
          !isRunningInstanceFilter &&
          isEqual(filter, {incidents: true}),
      };
      return conditions[type];
    }

    // Is 'dashboard' or 'filters' active;
    const conditions = {
      dashboard: currentView.isDashboard(),
      filters: currentView.isInstances() && !this.props.isFiltersCollapsed,
    };

    return conditions[type];
  }

  selectCount(type) {
    const {running, withIncidents, filterCount} = this.props.countStore;

    if (!this.state.isLoaded) {
      return '';
    }

    const conditions = {
      instances: running,
      filters: filterCount === null ? running : filterCount,
      incidents: withIncidents,
    };

    return conditions[type];
  }

  getLinkProperties(type) {
    const count = this.selectCount(type);

    return {
      count: count,
      isActive: this.selectActiveCondition(type),
      title: createTitle(type, count),
      dataTest: 'header-link-' + type,
      linkProps: this.getListLinksProps(type),
    };
  }

  renderInstanceDetails() {
    if (this.state.instance) {
      return <InstanceDetail instance={this.state.instance} />;
    } else {
      return (
        <>
          <Styled.SkeletonCircle />
          <Styled.SkeletonBlock />
        </>
      );
    }
  }

  render() {
    if (this.state.forceRedirect) {
      return <Redirect to="/login" />;
    }

    const brand = this.getLinkProperties('brand');
    const dashboard = this.getLinkProperties('dashboard');
    const instances = this.getLinkProperties('instances');
    const incidents = this.getLinkProperties('incidents');
    const filters = this.getLinkProperties('filters');

    return (
      <Styled.Header role="banner">
        <Styled.Menu role="navigation">
          <BrandNavElement
            to="/"
            dataTest={brand.dataTest}
            title={brand.title}
            label={labels['brand']}
          />
          <LinkElement
            dataTest={dashboard.dataTest}
            to="/"
            isActive={dashboard.isActive}
            title={dashboard.title}
            label={labels['dashboard']}
          />
          <NavElement
            dataTest={instances.dataTest}
            isActive={instances.isActive}
            title={instances.title}
            label={labels['instances']}
            count={instances.count}
            linkProps={instances.linkProps}
            type={BADGE_TYPE.RUNNING_INSTANCES}
          />
          <Styled.FilterNavElement
            dataTest={filters.dataTest}
            isActive={filters.isActive}
            title={filters.title}
            label={labels['filters']}
            count={filters.count}
            linkProps={filters.linkProps}
            type={BADGE_TYPE.FILTERS}
          />
          <NavElement
            dataTest={incidents.dataTest}
            isActive={incidents.isActive}
            title={incidents.title}
            label={labels['incidents']}
            count={incidents.count}
            linkProps={incidents.linkProps}
            type={BADGE_TYPE.INCIDENTS}
          />
        </Styled.Menu>
        {this.currentView().isInstance() && (
          <Styled.Detail>{this.renderInstanceDetails()}</Styled.Detail>
        )}
        <User handleRedirect={this.handleRedirect} />
      </Styled.Header>
    );
  }
}

const contexts = [
  withCountStore,
  withData,
  withCollapsablePanel,
  withSharedState,
  withRouter,
  withData,
];

const WrappedHeader = wrapWithContexts(contexts, Header);

WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
