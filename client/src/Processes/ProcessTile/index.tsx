/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InlineLoadingStatus, Stack} from '@carbon/react';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {notificationsStore} from 'modules/stores/notifications';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {useState} from 'react';
import {Container, Title, Subtitle} from './styled';
import {useNavigate, useMatch, useLocation} from 'react-router-dom';
import {pages} from 'modules/routing';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';
import {useStartProcess} from 'modules/mutations/useStartProcess';
import {Process, Task} from 'modules/types';
import {FormModal} from './FormModal';
import {getProcessDisplayName} from 'modules/utils/getProcessDisplayName';

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

function convertStatus(status: LoadingStatus): InlineLoadingStatus {
  if (status === 'active-tasks') {
    return 'active';
  }

  return status;
}

function getAsyncButtonDescription(status: LoadingStatus) {
  if (status === 'active') {
    return 'Starting process...';
  }

  if (status === 'active-tasks') {
    return 'Waiting for tasks...';
  }

  if (status === 'finished') {
    return 'Process started';
  }

  if (status === 'error') {
    return 'Process start failed';
  }

  return '';
}

const isMultiTenancyEnabled =
  window.clientConfig?.isMultiTenancyEnabled ?? false;

type Props = {
  process: Process;
  className?: string;
  isFirst: boolean;
  isStartButtonDisabled: boolean;
  'data-testid'?: string;
  tenantId?: Task['tenantId'];
};

const ProcessTile: React.FC<Props> = ({
  process,
  isFirst,
  isStartButtonDisabled,
  tenantId,
  ...props
}) => {
  const {mutateAsync: startProcess} = useStartProcess({
    onSuccess(data) {
      tracking.track({
        eventName: 'process-started',
      });
      setStatus('active-tasks');

      newProcessInstance.setInstance({
        ...data,
        removeCallback: (tasks = []) => {
          setStatus('finished');
          if (tasks === null) {
            tracking.track({
              eventName: 'process-tasks-polling-ended',
              outcome: 'navigated-away',
            });
            notificationsStore.displayNotification({
              isDismissable: true,
              kind: 'info',
              title: 'The task will appear in the list once it is created.',
            });

            return;
          }

          if (tasks.length === 0) {
            tracking.track({
              eventName: 'process-tasks-polling-ended',
              outcome: 'no-tasks-found',
            });
            notificationsStore.displayNotification({
              isDismissable: true,
              kind: 'info',
              title: "We couldn't find a task for the started process.",
              subtitle:
                'Your process might have not reached a user task yet, your user might not have rights to the task or the process might have an incident.',
            });

            return;
          }

          if (tasks.length > 1) {
            tracking.track({
              eventName: 'process-tasks-polling-ended',
              outcome: 'multiple-tasks-found',
            });
            tasks.forEach(({name, id, processName}) => {
              notificationsStore.displayNotification({
                isDismissable: false,
                kind: 'success',
                title: `Process "${processName}" reached task "${name}"`,
                isActionable: true,
                actionButtonLabel: 'Open task',
                onActionButtonClick: () => {
                  tracking.track({
                    eventName: 'process-task-toast-clicked',
                  });
                  navigate({pathname: pages.taskDetails(id)});
                },
              });
            });

            return;
          }
        },
      });
      notificationsStore.displayNotification({
        isDismissable: true,
        kind: 'success',
        title: 'Process has started',
        subtitle: 'We will redirect you to the task once it is created',
      });
    },
  });
  const [status, setStatus] = useState<LoadingStatus>('inactive');
  const {bpmnProcessId, startEventFormId} = process;
  const displayName = getProcessDisplayName(process);
  const location = useLocation();
  const navigate = useNavigate();
  const startFormModalRoute = pages.interalStartProcessFromForm(bpmnProcessId);
  const match = useMatch(startFormModalRoute);
  const isFormModalOpen = match !== null;

  return (
    <Container {...props}>
      <Stack>
        <Title>{displayName}</Title>
        <Subtitle>
          {displayName === bpmnProcessId ? '' : bpmnProcessId}
        </Subtitle>
        <AsyncActionButton
          status={convertStatus(status)}
          buttonProps={{
            type: 'button',
            kind: 'tertiary',
            size: 'sm',
            id: isFirst ? 'main-content' : '',
            autoFocus: isFirst,
            disabled: isStartButtonDisabled,
            onClick: async () => {
              if (startEventFormId === null) {
                setStatus('active');
                tracking.track({
                  eventName: 'process-start-clicked',
                });
                try {
                  await startProcess({
                    bpmnProcessId,
                    tenantId,
                  });
                } catch (error) {
                  logger.error(error);
                  setStatus('error');
                }
              } else {
                navigate({
                  ...location,
                  pathname: startFormModalRoute,
                });
              }
            },
          }}
          onError={() => {
            tracking.track({
              eventName: 'process-start-failed',
            });
            setStatus('inactive');
            if (isMultiTenancyEnabled && tenantId === undefined) {
              notificationsStore.displayNotification({
                isDismissable: false,
                kind: 'error',
                title: 'Process start failed. Please select a tenant.',
                subtitle: displayName,
              });
            } else {
              notificationsStore.displayNotification({
                isDismissable: false,
                kind: 'error',
                title: 'Process start failed',
                subtitle: displayName,
              });
            }
          }}
          inlineLoadingProps={{
            description: getAsyncButtonDescription(status),
            'aria-live': ['error', 'finished'].includes(status)
              ? 'assertive'
              : 'polite',
            onSuccess: () => {
              setStatus('inactive');
            },
          }}
        >
          Start process
        </AsyncActionButton>
      </Stack>

      {startEventFormId === null ? null : (
        <FormModal
          key={process.bpmnProcessId}
          process={process}
          isOpen={isFormModalOpen}
          onClose={() => {
            navigate({
              ...location,
              pathname: '/processes',
            });
          }}
          onSubmit={async (variables) => {
            await startProcess({
              bpmnProcessId,
              variables,
              tenantId,
            });
            navigate({
              ...location,
              pathname: '/processes',
            });
          }}
          isMultiTenancyEnabled={isMultiTenancyEnabled}
          tenantId={tenantId}
        />
      )}
    </Container>
  );
};

export {ProcessTile};
