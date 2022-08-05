/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {Form, LabeledInput, Modal, MessageBox, Button, DocsLink} from 'components';
import {t} from 'translation';

export default function VisibleEventsModal({initialScope, onClose, onConfirm}) {
  const [scope, setScope] = useState(initialScope);

  const toggleScopeItem = (item) => {
    if (scope.includes(item)) {
      return setScope(scope.filter((i) => i !== item));
    }
    return setScope([...scope, item]);
  };

  const updateSource = () => scope.length > 0 && onConfirm(scope);

  return (
    <Modal open onClose={onClose} onConfirm={updateSource}>
      <Modal.Header>{t('events.sources.editScope')}</Modal.Header>
      <Modal.Content>
        <Form description={t('events.sources.eventListTip')}>
          <Form.Group>
            <LabeledInput
              label={t('events.sources.startAndEnd')}
              checked={scope.includes('process_instance')}
              onChange={() => toggleScopeItem('process_instance')}
              type="checkbox"
            />
            <LabeledInput
              checked={scope.includes('start_end')}
              onChange={() => toggleScopeItem('start_end')}
              label={t('events.sources.flownodeEvents')}
              type="checkbox"
            />
            <LabeledInput
              label={t('events.sources.allEvents')}
              checked={scope.includes('all')}
              onChange={() => toggleScopeItem('all')}
              type="checkbox"
            />
          </Form.Group>
          <MessageBox type="warning">
            {t('events.sources.eventListChangeWarning')}{' '}
            <DocsLink location="components/optimize/userguide/additional-features/event-based-processes/#camunda-events">
              {t('events.sources.learnMore')}
            </DocsLink>
          </MessageBox>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!scope.length} main primary className="confirm" onClick={updateSource}>
          {t('common.update')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
