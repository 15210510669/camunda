/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Button, Modal, LabeledInput} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';
import {isMetadataTelemetryEnabled, loadConfig} from 'config';

import {updateTelemetry} from './service';

import './TelemetrySettings.scss';

export function TelemetrySettings({onClose, mightFail}) {
  const [telemetryEnabled, setTelemetryEnabled] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // set initial state of the checkbox
  useEffect(() => {
    (async () => {
      setTelemetryEnabled(await isMetadataTelemetryEnabled());
    })();
  }, []);

  function submit() {
    setIsLoading(true);
    mightFail(
      updateTelemetry(telemetryEnabled),
      () => {
        addNotification({type: 'success', text: t('telemetry.updated')});

        // ui-configuration has changed, we need to reload the config
        loadConfig();

        onClose();
      },
      (err) => {
        showError(err);
        setIsLoading(false);
      }
    );
  }

  return (
    <Modal className="TelemetrySettings" open onClose={onClose}>
      <Modal.Header>{t('telemetry.header')}</Modal.Header>
      <Modal.Content>
        <p>{t('telemetry.text')}</p>
        <div className="options">
          <LabeledInput
            type="checkbox"
            label={
              <>
                <h2>{t('telemetry.enable')}</h2>
                <p>{t('telemetry.info')}</p>
              </>
            }
            checked={telemetryEnabled}
            onChange={(evt) => setTelemetryEnabled(evt.target.checked)}
          />
        </div>
        <p>
          {t('telemetry.personalData')}
          <br />
          {t('telemetry.learnMore')}
          <a href="https://camunda.com/legal/privacy/" target="_blank" rel="noopener noreferrer">
            {t('telemetry.privacyPolicy')}
          </a>
        </p>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary className="apply" disabled={isLoading} onClick={submit}>
          {t('common.save')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(TelemetrySettings);
