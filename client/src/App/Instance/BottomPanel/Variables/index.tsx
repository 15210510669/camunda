/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';

import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';

import * as Styled from './styled';
import {observer} from 'mobx-react';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {VariableBackdrop} from './VariableBackdrop';
import {Skeleton} from './Skeleton';
import {Table, TH, TR} from './VariablesTable';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {ExistingVariable} from './ExistingVariable';
import {NewVariable} from './NewVariable';
import {PendingVariable} from './PendingVariable';
import {useForm, useFormState} from 'react-final-form';
import {useInstancePageParams} from '../../useInstancePageParams';
import {MAX_VARIABLES_STORED} from 'modules/constants/variables';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useNotifications} from 'modules/notifications';
import {Restricted} from 'modules/components/Restricted';

const Variables: React.FC = observer(() => {
  const {
    state: {items, pendingItem, loadingItemId, status},
    displayStatus,
    scopeId,
  } = variablesStore;

  const scrollableContentRef = useRef<HTMLDivElement>(null);
  const variablesContentRef = useRef<HTMLDivElement>(null);
  const variableRowRef = useRef<HTMLTableRowElement>(null);
  const {processInstanceId} = useInstancePageParams();
  const notifications = useNotifications();

  const form = useForm();

  useEffect(() => {
    form.reset({});
  }, [form, scopeId]);

  const {initialValues} = useFormState();

  const isViewMode =
    initialValues === undefined || Object.values(initialValues).length === 0;

  const isAddMode = initialValues?.name === '' && initialValues?.value === '';

  const isVariableHeaderVisible =
    isAddMode || variablesStore.displayStatus === 'variables';

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {displayStatus === 'spinner' && (
          <Styled.EmptyPanel
            data-testid="variables-spinner"
            type="skeleton"
            Skeleton={SpinnerSkeleton}
          />
        )}

        {isViewMode && displayStatus === 'skeleton' && (
          <Skeleton type="skeleton" rowHeight={32} />
        )}
        {isViewMode && displayStatus === 'no-variables' && (
          <Skeleton type="info" label="The Flow Node has no Variables" />
        )}
        {(!isViewMode || displayStatus === 'variables') && (
          <>
            <Styled.Header>Variables</Styled.Header>

            <Styled.TableScroll ref={scrollableContentRef}>
              <Table data-testid="variables-list">
                <Styled.THead
                  isVariableHeaderVisible={isVariableHeaderVisible}
                  scrollBarWidth={
                    (scrollableContentRef?.current?.offsetWidth ?? 0) -
                    (scrollableContentRef?.current?.scrollWidth ?? 0)
                  }
                >
                  {isVariableHeaderVisible && (
                    <TR>
                      <TH>Name</TH>
                      <TH>Value</TH>
                      <TH />
                    </TR>
                  )}
                </Styled.THead>
                <InfiniteScroller
                  onVerticalScrollStartReach={async (scrollDown) => {
                    if (
                      variablesStore.shouldFetchPreviousVariables() === false
                    ) {
                      return;
                    }
                    await variablesStore.fetchPreviousVariables(
                      processInstanceId
                    );

                    if (
                      variablesStore.state.items.length ===
                        MAX_VARIABLES_STORED &&
                      variablesStore.state.latestFetch.itemsCount !== 0
                    ) {
                      scrollDown(
                        variablesStore.state.latestFetch.itemsCount *
                          (variableRowRef.current?.offsetHeight ?? 0)
                      );
                    }
                  }}
                  onVerticalScrollEndReach={() => {
                    if (variablesStore.shouldFetchNextVariables() === false) {
                      return;
                    }
                    variablesStore.fetchNextVariables(processInstanceId);
                  }}
                  scrollableContainerRef={scrollableContentRef}
                >
                  <tbody>
                    {items.map(
                      ({
                        name: variableName,
                        value: variableValue,
                        hasActiveOperation,
                        isPreview,
                        id,
                      }) => (
                        <TR
                          ref={variableRowRef}
                          key={variableName}
                          data-testid={variableName}
                          hasActiveOperation={hasActiveOperation}
                        >
                          {initialValues?.name === variableName &&
                          currentInstanceStore.isRunning ? (
                            <ExistingVariable
                              variableName={variableName}
                              variableValue={variableValue}
                            />
                          ) : (
                            <>
                              <Styled.TD>
                                <Styled.VariableName title={variableName}>
                                  {variableName}
                                </Styled.VariableName>
                              </Styled.TD>

                              <Styled.DisplayTextTD>
                                <Styled.DisplayText
                                  hasBackdrop={loadingItemId === id}
                                >
                                  {loadingItemId === id && <VariableBackdrop />}
                                  {variableValue}
                                </Styled.DisplayText>
                              </Styled.DisplayTextTD>
                              <Styled.EditButtonsTD>
                                {currentInstanceStore.isRunning && (
                                  <>
                                    {hasActiveOperation ? (
                                      <Styled.Spinner data-testid="edit-variable-spinner" />
                                    ) : (
                                      <Restricted scopes={['write']}>
                                        <Styled.EditButton
                                          title="Enter edit mode"
                                          type="button"
                                          data-testid="edit-variable-button"
                                          disabled={loadingItemId !== null}
                                          onClick={async () => {
                                            let value = variableValue;
                                            if (isPreview) {
                                              const variable =
                                                await variablesStore.fetchVariable(
                                                  {
                                                    id,
                                                    onError: () => {
                                                      notifications.displayNotification(
                                                        'error',
                                                        {
                                                          headline:
                                                            'Variable could not be fetched',
                                                        }
                                                      );
                                                    },
                                                  }
                                                );

                                              if (variable === null) {
                                                return;
                                              }

                                              value = variable.value;
                                            }

                                            form.reset({
                                              name: variableName,
                                              value,
                                            });
                                            form.change('value', value);
                                          }}
                                          size="large"
                                          iconButtonTheme="default"
                                          icon={<Styled.EditIcon />}
                                        />
                                      </Restricted>
                                    )}
                                  </>
                                )}
                              </Styled.EditButtonsTD>
                            </>
                          )}
                        </TR>
                      )
                    )}
                  </tbody>
                </InfiniteScroller>
              </Table>
            </Styled.TableScroll>
          </>
        )}
        <Restricted scopes={['write']}>
          <Styled.Footer
            scrollBarWidth={
              (scrollableContentRef?.current?.offsetWidth ?? 0) -
              (scrollableContentRef?.current?.scrollWidth ?? 0)
            }
            hasPendingVariable={pendingItem !== null}
          >
            {currentInstanceStore.isRunning && (
              <>
                {pendingItem !== null && <PendingVariable />}
                {isAddMode && pendingItem === null && <NewVariable />}
              </>
            )}

            {!isAddMode && pendingItem === null && (
              <Styled.Button
                type="button"
                title="Add variable"
                size="small"
                onClick={() => {
                  form.reset({name: '', value: ''});
                }}
                disabled={
                  status === 'first-fetch' ||
                  !isViewMode ||
                  (flowNodeSelectionStore.isRootNodeSelected
                    ? !currentInstanceStore.isRunning
                    : !flowNodeMetaDataStore.isSelectedInstanceRunning) ||
                  loadingItemId !== null
                }
              >
                <Styled.Plus /> Add Variable
              </Styled.Button>
            )}
          </Styled.Footer>
        </Restricted>
      </Styled.VariablesContent>
    </>
  );
});

export default Variables;
