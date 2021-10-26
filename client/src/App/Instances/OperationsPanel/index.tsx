/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';

import {operationsStore} from 'modules/stores/operations';
import {CollapsablePanel, OperationsList, EmptyMessage} from './styled';
import * as CONSTANTS from './constants';
import OperationsEntry from './OperationsEntry';
import Skeleton from './Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {panelStatesStore} from 'modules/stores/panelStates';

const OperationsPanel: React.FC = observer(() => {
  const {operations, status, hasMoreOperations} = operationsStore.state;
  const {
    state: {isOperationsCollapsed},
    toggleOperationsPanel,
  } = panelStatesStore;

  useEffect(() => {
    operationsStore.init();
    return operationsStore.reset;
  }, []);

  const scrollableContainerRef = useRef<HTMLDivElement | null>(null);

  return (
    <CollapsablePanel
      label={CONSTANTS.OPERATIONS_LABEL}
      panelPosition="RIGHT"
      maxWidth={478}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      toggle={toggleOperationsPanel}
      hasBackgroundColor
      verticalLabelOffset={27}
      scrollable
      ref={scrollableContainerRef}
    >
      <InfiniteScroller
        onVerticalScrollEndReach={() => {
          if (hasMoreOperations && status !== 'fetching') {
            operationsStore.fetchNextOperations();
          }
        }}
        scrollableContainerRef={scrollableContainerRef}
      >
        <OperationsList
          data-testid="operations-list"
          isInitialLoadComplete={status === 'fetched'}
        >
          {['initial', 'first-fetch'].includes(status) && <Skeleton />}
          {operations.map((operation) => (
            <OperationsEntry key={operation.id} operation={operation} />
          ))}
          {operations.length === 0 && status === 'fetched' && (
            <EmptyMessage>{CONSTANTS.EMPTY_MESSAGE}</EmptyMessage>
          )}
          {status === 'error' && (
            <EmptyMessage>
              <StatusMessage variant="error">
                Operations could not be fetched
              </StatusMessage>
            </EmptyMessage>
          )}
        </OperationsList>
      </InfiniteScroller>
    </CollapsablePanel>
  );
});

export {OperationsPanel};
