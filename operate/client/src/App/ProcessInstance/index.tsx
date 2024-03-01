/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {InstanceDetail} from '../Layout/InstanceDetail';
import {Breadcrumb} from './Breadcrumb';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from './useProcessInstancePageParams';
import {useLocation, useNavigate} from 'react-router-dom';
import {useEffect} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {reaction, when} from 'mobx';
import {variablesStore} from 'modules/stores/variables';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {Locations} from 'modules/Routes';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {TopPanel} from './TopPanel';
import {BottomPanel, ModificationFooter, Buttons} from './styled';
import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {Button, Modal} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {ModificationSummaryModal} from './ModificationSummaryModal';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {LastModification} from './LastModification';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {Forbidden} from 'modules/components/Forbidden';
import {notificationsStore} from 'modules/stores/notifications';
import {Frame} from 'modules/components/Frame';

const startPolling = (processInstanceId: ProcessInstanceEntity['id']) => {
  variablesStore.startPolling(processInstanceId, {runImmediately: true});
  sequenceFlowsStore.startPolling(processInstanceId, {runImmediately: true});
  processInstanceDetailsStore.startPolling(processInstanceId, {
    runImmediately: true,
  });
  incidentsStore.startPolling(processInstanceId, {
    runImmediately: true,
  });
  flowNodeInstanceStore.startPolling({runImmediately: true});
  processInstanceDetailsStatisticsStore.startPolling(processInstanceId, {
    runImmediately: true,
  });
};

const stopPolling = () => {
  variablesStore.stopPolling();
  sequenceFlowsStore.stopPolling();
  processInstanceDetailsStore.stopPolling();
  incidentsStore.stopPolling();
  flowNodeInstanceStore.stopPolling();
  processInstanceDetailsStatisticsStore.stopPolling();
};

const ProcessInstance: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const navigate = useNavigate();
  const location = useLocation();

  const {showPrompt, confirmNavigation, cancelNavigation} = useCallbackPrompt(
    modificationsStore.isModificationModeEnabled,
  );

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      (isModificationModeEnabled) => {
        if (isModificationModeEnabled) {
          stopPolling();
        } else {
          instanceHistoryModificationStore.reset();
          startPolling(processInstanceId);
        }
      },
    );

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        startPolling(processInstanceId);
      } else {
        stopPolling();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      disposer();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [processInstanceId]);

  useEffect(() => {
    const {
      state: {processInstance},
    } = processInstanceDetailsStore;

    if (processInstanceId !== processInstance?.id) {
      processInstanceDetailsStore.init({
        id: processInstanceId,
        onRefetchFailure: () => {
          navigate(
            Locations.processes({
              active: true,
              incidents: true,
            }),
          );

          notificationsStore.displayNotification({
            kind: 'error',
            title: `Instance ${processInstanceId} could not be found`,
            isDismissable: true,
          });
        },
        onPollingFailure: () => {
          navigate(Locations.processes());

          notificationsStore.displayNotification({
            kind: 'success',
            title: 'Instance deleted',
            isDismissable: true,
          });
        },
      });
      flowNodeInstanceStore.init();
      processInstanceDetailsStatisticsStore.init(processInstanceId);
      processInstanceDetailsDiagramStore.init();
      flowNodeSelectionStore.init();
    }
  }, [processInstanceId, navigate, location]);

  useEffect(() => {
    return () => {
      instanceHistoryModificationStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
      flowNodeInstanceStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeTimeStampStore.reset();
      flowNodeSelectionStore.reset();
      modificationsStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    let processTitleDisposer = when(
      () => processInstanceDetailsStore.processTitle !== null,
      () => {
        document.title = processInstanceDetailsStore.processTitle ?? '';
      },
    );

    return () => {
      processTitleDisposer();
    };
  }, []);

  const {
    isModificationModeEnabled,
    state: {modifications, status: modificationStatus},
  } = modificationsStore;

  const isBreadcrumbVisible =
    processInstanceDetailsStore.state.processInstance !== null &&
    processInstanceDetailsStore.state.processInstance?.callHierarchy?.length >
      0;

  const hasPendingModifications = modifications.length > 0;

  if (processInstanceDetailsStore.state.status === 'forbidden') {
    return <Forbidden />;
  }

  return (
    <>
      <VisuallyHiddenH1>
        {`Operate Process Instance${
          isModificationModeEnabled ? ' - Modification Mode' : ''
        }`}
      </VisuallyHiddenH1>
      <Frame
        frame={{
          isVisible: isModificationModeEnabled,
          headerTitle: 'Process Instance Modification Mode',
        }}
      >
        <InstanceDetail
          hasLoadingOverlay={modificationStatus === 'applying-modifications'}
          breadcrumb={
            isBreadcrumbVisible ? (
              <Breadcrumb
                processInstance={
                  processInstanceDetailsStore.state.processInstance!
                }
              />
            ) : undefined
          }
          header={<ProcessInstanceHeader />}
          topPanel={<TopPanel />}
          bottomPanel={
            <BottomPanel>
              <FlowNodeInstanceLog />
              <VariablePanel />
            </BottomPanel>
          }
          footer={
            isModificationModeEnabled ? (
              <ModificationFooter>
                <LastModification />
                <Buttons orientation="horizontal" gap={4}>
                  <ModalStateManager
                    renderLauncher={({setOpen}) => (
                      <Button
                        kind="secondary"
                        size="sm"
                        onClick={() => {
                          tracking.track({
                            eventName: 'discard-all-summary',
                            hasPendingModifications,
                          });
                          setOpen(true);
                        }}
                        data-testid="discard-all-button"
                      >
                        Discard All
                      </Button>
                    )}
                  >
                    {({open, setOpen}) => (
                      <Modal
                        modalHeading="Discard Modifications"
                        preventCloseOnClickOutside
                        danger
                        primaryButtonText="Discard"
                        secondaryButtonText="Cancel"
                        open={open}
                        onRequestClose={() => setOpen(false)}
                        onRequestSubmit={() => {
                          tracking.track({
                            eventName: 'discard-modifications',
                            hasPendingModifications,
                          });
                          modificationsStore.reset();
                          setOpen(false);
                        }}
                      >
                        <p>
                          About to discard all added modifications for instance{' '}
                          {processInstanceId}.
                        </p>
                        <p>Click "Discard" to proceed.</p>
                      </Modal>
                    )}
                  </ModalStateManager>
                  <ModalStateManager
                    renderLauncher={({setOpen}) => (
                      <Button
                        kind="primary"
                        size="sm"
                        onClick={() => {
                          tracking.track({
                            eventName: 'apply-modifications-summary',
                            hasPendingModifications,
                          });
                          setOpen(true);
                        }}
                        data-testid="apply-modifications-button"
                        disabled={!hasPendingModifications}
                      >
                        Apply Modifications
                      </Button>
                    )}
                  >
                    {({open, setOpen}) => (
                      <ModificationSummaryModal open={open} setOpen={setOpen} />
                    )}
                  </ModalStateManager>
                </Buttons>
              </ModificationFooter>
            ) : undefined
          }
          type="process"
        />
      </Frame>
      {showPrompt && (
        <Modal
          open={showPrompt}
          modalHeading="Leave Modification Mode"
          preventCloseOnClickOutside
          onRequestClose={cancelNavigation}
          secondaryButtonText="Stay"
          primaryButtonText="Leave"
          onRequestSubmit={() => {
            tracking.track({eventName: 'leave-modification-mode'});
            confirmNavigation();
          }}
        >
          <p>
            By leaving this page, all planned modification/s will be discarded.
          </p>
        </Modal>
      )}
    </>
  );
});

export {ProcessInstance};
