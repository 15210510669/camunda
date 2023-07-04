/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {DangerButton} from 'modules/components/Carbon/OperationItem/DangerButton';
import {OperationItems} from 'modules/components/Carbon/OperationItems';
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import {DeleteDefinitionModal} from 'modules/components/Carbon/DeleteDefinitionModal';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useNotifications} from 'modules/notifications';
import {StructuredList} from 'modules/components/Carbon/StructuredList';
import {UnorderedList} from 'modules/components/Carbon/DeleteDefinitionModal/Warning/styled';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';

type Props = {
  decisionDefinitionId: string;
  decisionName: string;
  decisionVersion: string;
};

const DecisionOperations: React.FC<Props> = ({
  decisionDefinitionId,
  decisionName,
  decisionVersion,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  const notifications = useNotifications();
  const [isOperationRunning, setIsOperationRunning] = useState(false);

  return (
    <>
      <DeleteButtonContainer>
        {isOperationRunning && (
          <InlineLoading data-testid="delete-operation-spinner" />
        )}
        <OperationItems>
          <DangerButton
            title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
            type="DELETE"
            disabled={isOperationRunning}
            onClick={() => setIsDeleteModalVisible(true)}
          />
        </OperationItems>
      </DeleteButtonContainer>
      <DeleteDefinitionModal
        title="Delete DRD"
        description="You are about to delete the following DRD:"
        confirmationText="Yes, I confirm I want to delete this DRD and all related instances."
        isVisible={isDeleteModalVisible}
        warningTitle="Deleting a decision definition will delete the DRD and will impact
        the following:"
        warningContent={
          <Stack gap={6}>
            <UnorderedList nested>
              <ListItem>
                By deleting a decision definition, you will be deleting the DRD
                which contains this decision definition. All other decision
                tables and literal expressions that are part of the DRD will
                also be deleted.
              </ListItem>
              <ListItem>
                Deleting the only existing version of a decision definition
                could result in process incidents.
              </ListItem>
              <ListItem>
                In case the DRD contains decisions which are part of multiple
                DRDs, these decision definitions and their DRDs will not be
                deleted.
              </ListItem>
            </UnorderedList>
            <Link
              href="https://docs.camunda.io/docs/components/operate/operate-introduction/"
              target="_blank"
            >
              Read more about deleting a decision definition
            </Link>
          </Stack>
        }
        bodyContent={
          <StructuredList
            headerColumns={[
              {
                cellContent: 'DRD name',
              },
            ]}
            rows={[
              {
                key: decisionDefinitionStore.name ?? 'drdName',
                columns: [
                  {
                    cellContent: decisionDefinitionStore.name,
                  },
                ],
              },
            ]}
            label="DRD Details"
          />
        }
        onClose={() => setIsDeleteModalVisible(false)}
        onDelete={() => {
          setIsOperationRunning(true);
          setIsDeleteModalVisible(false);

          operationsStore.applyDeleteDecisionDefinitionOperation({
            decisionDefinitionId,
            onSuccess: panelStatesStore.expandOperationsPanel,
            onError: (statusCode: number) => {
              setIsOperationRunning(false);

              notifications.displayNotification('error', {
                headline: 'Operation could not be created',
                description:
                  statusCode === 403 ? 'You do not have permission' : undefined,
              });
            },
          });
        }}
      />
    </>
  );
};
export {DecisionOperations};
