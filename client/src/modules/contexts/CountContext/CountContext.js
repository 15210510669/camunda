/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useReducer, useEffect, useContext} from 'react';
import PropTypes from 'prop-types';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';
import {DataContext} from 'modules/DataManager';
import withSharedState from 'modules/components/withSharedState';

const CountContext = React.createContext({});

const initialState = {
  running: 0,
  active: 0,
  withIncidents: 0,
  filterCount: null,
  isLoaded: false,
};

export function countReducer(state, {type, payload}) {
  switch (type) {
    case 'filterCount':
      return {...state, filterCount: payload};
    case 'coreStats':
      return {...state, ...payload, isLoaded: true};
    default:
      throw new Error();
  }
}

export function Provider(props) {
  const subscriptions = {
    LOAD_CORE_STATS: ({state, response}) => {
      if (state === LOADING_STATE.LOADED) {
        dispatch({
          type: 'coreStats',
          payload: response.coreStatistics,
        });
      }
    },
    REFRESH_AFTER_OPERATION: ({state, response}) => {
      if (state === LOADING_STATE.LOADED) {
        const {
          LOAD_CORE_STATS: {coreStatistics},
          LOAD_LIST_INSTANCES: {totalCount},
        } = response;

        dispatch({
          type: 'coreStats',
          payload: coreStatistics,
        });
        dispatch({
          type: 'filterCount',
          payload: totalCount,
        });
      }
    },
    CONSTANT_REFRESH: ({state, response}) => {
      if (state === LOADING_STATE.LOADED) {
        dispatch({
          type: 'coreStats',
          payload: response[SUBSCRIPTION_TOPIC.LOAD_CORE_STATS].coreStatistics,
        });
      }
    },
    LOAD_LIST_INSTANCES: ({response, state}) => {
      if (state === LOADING_STATE.LOADED) {
        dispatch({
          type: 'filterCount',
          payload: response.totalCount,
        });
      }
    },
  };
  const {dataManager} = useContext(DataContext);
  const [store, dispatch] = useReducer(countReducer, initialState);

  useEffect(() => {
    dataManager.subscribe(subscriptions);
    return () => dataManager.unsubscribe(subscriptions);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    dataManager.getWorkflowCoreStatistics();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const {filterCount} = props.getStateLocally();

    filterCount !== 'null' &&
      typeof filterCount !== 'undefined' &&
      dispatch({
        type: 'filterCount',
        payload: filterCount,
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <CountContext.Provider value={store}>
      {props.children}
    </CountContext.Provider>
  );
}

Provider.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
  getStateLocally: PropTypes.func,
};

function withCountStore(Component) {
  function withCountStore(props) {
    return (
      <CountContext.Consumer>
        {(countStore) => <Component {...props} countStore={countStore} />}
      </CountContext.Consumer>
    );
  }

  withCountStore.WrappedComponent = Component;

  withCountStore.displayName = `WithStore(${
    Component.displayName || Component.name || 'Component'
  })`;

  return withCountStore;
}

const CountStoreProvider = withSharedState(Provider);

export {CountStoreProvider, withCountStore, CountContext};
