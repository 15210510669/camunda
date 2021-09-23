/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';

type Props = {
  onModalClose: () => void;
  rootInstanceId: string;
  isVisible: boolean;
};

const CalledInstanceCancellationModal: React.FC<Props> = ({
  onModalClose,
  isVisible,
  rootInstanceId,
}) => (
  <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.SMALL}>
    <Modal.Header>Cancel Instance</Modal.Header>
    <Modal.Body>
      <Modal.BodyText>
        To cancel this instance, the root instance{' '}
        <Link
          to={(location) => Locations.instance(rootInstanceId, location)}
          title={`View root instance ${rootInstanceId}`}
        >
          {rootInstanceId}
        </Link>{' '}
        needs to be canceled. When the root instance is canceled all the called
        instances will be canceled automatically.
      </Modal.BodyText>
    </Modal.Body>
    <Modal.Footer>
      <Modal.SecondaryButton title="Close" onClick={onModalClose}>
        Close
      </Modal.SecondaryButton>
    </Modal.Footer>
  </Modal>
);

export {CalledInstanceCancellationModal};
