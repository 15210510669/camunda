/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {
  TableBatchAction,
  Stack,
  ComposedModal,
  ModalHeader,
  ModalBody,
  Button,
  ModalFooter,
} from '@carbon/react';
import {Move} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import isNil from 'lodash/isNil';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import modalButtonsImageLight from './images/modal-buttons-image-light.png';
import modalButtonsImageDark from './images/modal-buttons-image-dark.png';
import modalDiagramImageLight from './images/modal-diagram-image-light.png';
import modalDiagramImageDark from './images/modal-diagram-image-dark.png';
import {currentTheme} from 'modules/stores/currentTheme';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {Checkbox} from './styled';
import {batchModificationStore} from 'modules/stores/batchModification';

const MoveAction: React.FC = observer(() => {
  const location = useLocation();
  const {process, tenant, flowNodeId} = getProcessInstanceFilters(
    location.search,
  );

  const {hasSelectedRunningInstances} = processInstancesSelectionStore;

  const businessObject: BusinessObject | null = flowNodeId
    ? processXmlStore.state.diagramModel?.elementsById[flowNodeId]
    : null;

  const isTypeSupported = (businessObject: BusinessObject) => {
    return (
      businessObject.$type !== 'bpmn:StartEvent' &&
      businessObject.$type !== 'bpmn:BoundaryEvent' &&
      !isMultiInstance(businessObject)
    );
  };

  const isDisabled =
    batchModificationStore.state.isEnabled ||
    isNil(businessObject) ||
    flowNodeId === undefined ||
    !isTypeSupported(businessObject) ||
    !hasSelectedRunningInstances ||
    isWithinMultiInstance(businessObject) ||
    isAttachedToAnEventBasedGateway(businessObject);

  const getTooltipText = () => {
    if (!isDisabled) {
      return undefined;
    }

    if (flowNodeId === undefined || isNil(businessObject)) {
      return 'Please select an element from the diagram first.';
    }
    if (!isTypeSupported(businessObject)) {
      return 'The selected element type is not supported.';
    }
    if (!hasSelectedRunningInstances) {
      return 'You can only move flow node instances in active or incident state.';
    }
    if (isWithinMultiInstance(businessObject)) {
      return 'Elements inside a multi instance element are not supported.';
    }
    if (isAttachedToAnEventBasedGateway(businessObject)) {
      return 'Elements attached to an event based gateway are not supported.';
    }
  };

  return (
    <Restricted
      scopes={['write']}
      resourceBasedRestrictions={{
        scopes: ['UPDATE_PROCESS_INSTANCE'],
        permissions: processesStore.getPermissions(process, tenant),
      }}
    >
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <TableBatchAction
            renderIcon={Move}
            onClick={() => {
              if (getStateLocally()?.hideMoveModificationHelperModal) {
                batchModificationStore.enable();
              } else {
                setOpen(true);
              }
            }}
            disabled={isDisabled}
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : getTooltipText()
            }
          >
            Move
          </TableBatchAction>
        )}
      >
        {({open, setOpen}) => (
          <ComposedModal
            open={open}
            preventCloseOnClickOutside
            size="md"
            aria-label="Process instance batch move mode"
            onClose={() => setOpen(false)}
          >
            <ModalHeader title="Process instance batch move mode" />
            <ModalBody>
              <Stack gap={5}>
                <div>
                  This mode allows you to move multiple instances as a batch in
                  a one operation
                </div>
                <div>1. Click on the target flow node.</div>
                {currentTheme.theme === 'light' ? (
                  <img
                    src={modalDiagramImageLight}
                    alt="A bpmn diagram with a selected flow node"
                  />
                ) : (
                  <img
                    src={modalDiagramImageDark}
                    alt="A bpmn diagram with a selected flow node"
                  />
                )}
                <div>2. Apply</div>
                {currentTheme.theme === 'light' ? (
                  <img
                    src={modalButtonsImageLight}
                    alt="A button with the label Apply Modifications"
                  />
                ) : (
                  <img
                    src={modalButtonsImageDark}
                    alt="A button with the label Apply Modifications"
                  />
                )}
              </Stack>
            </ModalBody>
            <ModalFooter>
              <Checkbox
                labelText="Do not show this message again"
                id="do-not-show"
                onChange={(_, {checked}) => {
                  storeStateLocally({
                    hideMoveModificationHelperModal: checked,
                  });
                }}
              />
              <Button
                kind="primary"
                onClick={() => {
                  setOpen(false);
                  batchModificationStore.enable();
                }}
              >
                Continue
              </Button>
            </ModalFooter>
          </ComposedModal>
        )}
      </ModalStateManager>
    </Restricted>
  );
});

export {MoveAction};
