/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Skeleton from './Skeleton';

import EmptyPanel from 'modules/components/EmptyPanel';

import {withData} from 'modules/DataManager';

import {LOADING_STATE} from 'modules/constants';

import * as Styled from './styled';

import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';

class FlowNodeInstanceLog extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
    diagramDefinitions: PropTypes.object,
    activityInstancesTree: PropTypes.object,
    getNodeWithMetaData: PropTypes.func,
    onTreeRowSelection: PropTypes.func,
  };

  constructor(props) {
    super(props);

    this.state = {
      loadingStateInstanceTree: LOADING_STATE.LOADING,
      loadingStateDefinitions: LOADING_STATE.LOADING,
    };
    this.subscriptions = {
      LOAD_INSTANCE_TREE: ({state}) => {
        this.storeLoadingState(state, (loadedOrFailed) =>
          this.setState({loadingStateInstanceTree: loadedOrFailed})
        );
      },
      LOAD_STATE_DEFINITIONS: ({state}) => {
        this.storeLoadingState(state, (loadedOrFailed) =>
          this.setState({loadingStateDefinitions: loadedOrFailed})
        );
      },
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  storeLoadingState(state, callback) {
    if (state === LOADING_STATE.LOADED || state === LOADING_STATE.LOAD_FAILED) {
      callback(state);
    }
  }

  constructLabel() {
    let label, type;
    const combinedLoadingState = this.combineLoadingState();

    if (combinedLoadingState === LOADING_STATE.LOADING) {
      type = 'skeleton';
    } else if (combinedLoadingState === LOADING_STATE.LOAD_FAILED) {
      type = 'warning';
      label = `Activity Instances could not be fetched`;
    } else if (combinedLoadingState === LOADING_STATE.LOADED) {
      return null;
    }

    return {label, type};
  }

  combineLoadingState() {
    const {loadingStateInstanceTree, loadingStateDefinitions} = this.state;
    const {LOADING, LOAD_FAILED} = LOADING_STATE;

    if (
      loadingStateInstanceTree === LOAD_FAILED ||
      loadingStateDefinitions === LOAD_FAILED
    ) {
      return LOADING_STATE.LOAD_FAILED;
    }

    if (
      loadingStateInstanceTree === LOADING ||
      loadingStateDefinitions === LOADING
    ) {
      return LOADING_STATE.LOADING;
    }
  }

  render() {
    const {label, type} = this.constructLabel();

    const {
      diagramDefinitions,
      activityInstancesTree,
      getNodeWithMetaData,
      onTreeRowSelection,
    } = this.props;

    return (
      <Styled.Panel>
        {diagramDefinitions && Object.keys(activityInstancesTree).length > 0 ? (
          <Styled.FlowNodeInstanceLog>
            <Styled.NodeContainer>
              <FlowNodeInstancesTree
                node={activityInstancesTree}
                getNodeWithMetaData={getNodeWithMetaData}
                treeDepth={1}
                onTreeRowSelection={onTreeRowSelection}
              />
            </Styled.NodeContainer>
          </Styled.FlowNodeInstanceLog>
        ) : (
          <Styled.FlowNodeInstanceSkeleton>
            <EmptyPanel
              label={label}
              type={type}
              Skeleton={Skeleton}
              rowHeight={28}
            />
          </Styled.FlowNodeInstanceSkeleton>
        )}
      </Styled.Panel>
    );
  }
}

export default withData(FlowNodeInstanceLog);
