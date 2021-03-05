/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Redirect} from 'react-router';

import {Button, Modal, EntityList, Icon, DocsLink} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import EventsSourceModal from './EventsSourceModal';
import {createProcess} from './service';

import './GenerationModal.scss';

export function GenerationModal({onClose, mightFail}) {
  const [sources, setSources] = useState([]);
  const [openEventsSourceModal, setOpenEventsSourceModal] = useState(false);
  const [redirect, setRedirect] = useState();

  const removeSource = (target) => setSources(sources.filter((src) => src !== target));

  const onConfirm = async () =>
    mightFail(createProcess({eventSources: sources, autogenerate: true}), setRedirect, showError);

  if (redirect) {
    return <Redirect to={`/events/processes/${redirect}/generated`} />;
  }

  return (
    <Modal
      className="GenerationModal"
      open
      onClose={onClose}
      onConfirm={sources.length > 0 && onConfirm}
    >
      <Modal.Header>{t('events.autogenerate')}</Modal.Header>
      <Modal.Content>
        <p className="description">
          {t('events.generationInfo')}{' '}
          <DocsLink location="user-guide/event-based-processes#autogenerate">
            {t('events.sources.learnMore')}
          </DocsLink>
        </p>
        <EntityList
          embedded
          action={
            <Button onClick={() => setOpenEventsSourceModal(true)}>
              <Icon type="plus" />
              {t('events.sources.add')}
            </Button>
          }
          name={t('events.addedSources')}
          empty={t('home.sources.notCreated')}
          data={sources.map((source) => {
            const {
              configuration: {processDefinitionKey, processDefinitionName},
              type,
            } = source;

            const actions = [
              {
                icon: 'delete',
                text: t('common.remove'),
                action: () => removeSource(source),
              },
            ];

            if (type === 'external') {
              return {
                icon: 'data-source',
                type: t('events.sources.externalEvents'),
                name: t('events.sources.allExternal'),
                actions,
              };
            } else {
              return {
                icon: 'camunda-source',
                type: t('events.sources.camundaProcess'),
                name: processDefinitionName || processDefinitionKey,
                actions,
              };
            }
          })}
        />
        {openEventsSourceModal && (
          <EventsSourceModal
            autoGenerate
            existingSources={sources}
            onConfirm={(sources) => {
              setSources(sources);
              setOpenEventsSourceModal(false);
            }}
            onClose={() => setOpenEventsSourceModal(false)}
          />
        )}
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!sources.length} onClick={onConfirm}>
          {t('events.generate')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(GenerationModal);
