/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {OPERATION_TYPE} from 'modules/constants';
import {operationsStore} from 'modules/stores/operations';
import {instancesStore} from 'modules/stores/instances';
import {observer} from 'mobx-react';

import {hasIncident, isRunning} from 'modules/utils/instance';

import OperationItems from 'modules/components/OperationItems';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import {DeleteOperationModal} from './DeleteOperationModal';
import {ConfirmOperationModal} from 'modules/components/ConfirmOperationModal';
import {OperationsContainer} from './styled';
import {CalledInstanceCancellationModal} from './CalledInstanceCancellationModal';

type Props = {
  instance: ProcessInstanceEntity;
  isSelected?: boolean;
  onOperation?: () => void;
  onError?: () => void;
  forceSpinner?: boolean;
};

const Operations: React.FC<Props> = observer(
  ({instance, isSelected, onOperation, onError, forceSpinner}) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
    const [isCancellationModalVisible, setIsCancellationModalVisible] =
      useState(false);

    const applyOperation = async (
      operationType: InstanceOperationEntity['type']
    ) => {
      operationsStore.applyOperation({
        instanceId: instance.id,
        payload: {
          operationType,
        },
        onError,
      });

      onOperation?.();
    };

    return (
      <OperationsContainer>
        {(forceSpinner ||
          instancesStore.instanceIdsWithActiveOperations.includes(
            instance.id
          )) && (
          <OperationSpinner
            isSelected={isSelected}
            title={`Instance ${instance.id} has scheduled Operations`}
            data-testid="operation-spinner"
          />
        )}
        <OperationItems>
          {hasIncident(instance) && (
            <OperationItems.Item
              type={OPERATION_TYPE.RESOLVE_INCIDENT}
              onClick={() => applyOperation(OPERATION_TYPE.RESOLVE_INCIDENT)}
              title={`Retry Instance ${instance.id}`}
            />
          )}
          {isRunning(instance) && (
            <OperationItems.Item
              type={OPERATION_TYPE.CANCEL_PROCESS_INSTANCE}
              onClick={() => setIsCancellationModalVisible(true)}
              title={`Cancel Instance ${instance.id}`}
            />
          )}
          {!isRunning(instance) && (
            <OperationItems.Item
              type={OPERATION_TYPE.DELETE_PROCESS_INSTANCE}
              onClick={() => setIsDeleteModalVisible(true)}
              title={`Delete Instance ${instance.id}`}
            />
          )}
        </OperationItems>
        {instance.rootInstanceId === null ? (
          <ConfirmOperationModal
            bodyText={`About to cancel Instance ${instance.id}. In case there are called instances, these will be canceled too.`}
            onApplyClick={() => {
              setIsCancellationModalVisible(false);
              applyOperation(OPERATION_TYPE.CANCEL_PROCESS_INSTANCE);
            }}
            isVisible={isCancellationModalVisible}
            onModalClose={() => setIsCancellationModalVisible(false)}
            onCancelClick={() => setIsCancellationModalVisible(false)}
          />
        ) : (
          <CalledInstanceCancellationModal
            onModalClose={() => setIsCancellationModalVisible(false)}
            isVisible={isCancellationModalVisible}
            rootInstanceId={instance.rootInstanceId}
          />
        )}
        <DeleteOperationModal
          isVisible={isDeleteModalVisible}
          onModalClose={() => setIsDeleteModalVisible(false)}
          instanceId={instance.id}
          onDeleteClick={() => {
            applyOperation(OPERATION_TYPE.DELETE_PROCESS_INSTANCE);
            setIsDeleteModalVisible(false);
          }}
        />
      </OperationsContainer>
    );
  }
);

export {Operations};
