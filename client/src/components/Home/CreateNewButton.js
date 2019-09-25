/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function CreateNewButton({createCollection, collection}) {
  return (
    <Dropdown label={t('home.createBtn.default')} className="CreateNewButton">
      {!collection && (
        <Dropdown.Option onClick={createCollection}>
          {t('home.createBtn.collection')}
        </Dropdown.Option>
      )}
      <Dropdown.Option link="dashboard/new/edit">{t('home.createBtn.dashboard')}</Dropdown.Option>
      <Dropdown.Submenu label={t('home.createBtn.report.default')}>
        <Dropdown.Option link="report/new/edit">
          {t('home.createBtn.report.process')}
        </Dropdown.Option>
        <Dropdown.Option link="report/new-combined/edit">
          {t('home.createBtn.report.combined')}
        </Dropdown.Option>
        <Dropdown.Option link="report/new-decision/edit">
          {t('home.createBtn.report.decision')}
        </Dropdown.Option>
      </Dropdown.Submenu>
    </Dropdown>
  );
}
