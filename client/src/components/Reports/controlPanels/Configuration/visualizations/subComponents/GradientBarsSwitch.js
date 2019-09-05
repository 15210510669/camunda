/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch} from 'components';
import {t} from 'translation';

export default function GradientBarsSwitch({configuration, onChange}) {
  return (
    <Switch
      checked={!!configuration.showGradientBars}
      onChange={({target: {checked}}) => onChange({showGradientBars: {$set: checked}})}
      label={t('report.config.showGradientBars')}
    />
  );
}
