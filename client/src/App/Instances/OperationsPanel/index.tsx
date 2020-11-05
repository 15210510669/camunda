/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import {operationsStore} from 'modules/stores/operations';
import * as Styled from './styled';
import * as CONSTANTS from './constants';
import {hasOperations} from './service';
import OperationsEntry from './OperationsEntry';
import Skeleton from './Skeleton';
import {observer} from 'mobx-react';

type Props = {
  isOperationsCollapsed: boolean;
  toggleOperations: () => void;
};

const OperationsPanel: React.FC<Props> = observer(
  ({isOperationsCollapsed, toggleOperations}: any) => {
    const {operations, isInitialLoadComplete} = operationsStore.state;

    useEffect(() => {
      operationsStore.init();
      return operationsStore.reset;
    }, []);

    return (
      <CollapsablePanel
        label={CONSTANTS.OPERATIONS_LABEL}
        panelPosition={PANEL_POSITION.RIGHT}
        maxWidth={478}
        isOverlay
        isCollapsed={isOperationsCollapsed}
        toggle={toggleOperations}
        hasBackgroundColor
        verticalLabelOffset={27}
        scrollable={isInitialLoadComplete}
        onScroll={(event) => {
          const target = event.target as HTMLDivElement;

          if (
            target.scrollHeight - target.clientHeight - target.scrollTop <= 0 &&
            operations.length > 0
          ) {
            operationsStore.fetchOperations(
              // @ts-expect-error ts-migrate(2339) FIXME: Property 'sortValues' does not exist on type 'neve... Remove this comment to see the full error message
              operations[operations.length - 1].sortValues
            );
          }
        }}
      >
        <Styled.OperationsList
          data-testid="operations-list"
          isInitialLoadComplete={isInitialLoadComplete}
        >
          {!isInitialLoadComplete ? (
            <Skeleton />
          ) : hasOperations(operations) ? (
            operations.map((operation) => (
              // @ts-expect-error
              <OperationsEntry key={operation.id} operation={operation} />
            ))
          ) : (
            <Styled.EmptyMessage>{CONSTANTS.EMPTY_MESSAGE}</Styled.EmptyMessage>
          )}
        </Styled.OperationsList>
      </CollapsablePanel>
    );
  }
);

export default withCollapsablePanel(OperationsPanel);
