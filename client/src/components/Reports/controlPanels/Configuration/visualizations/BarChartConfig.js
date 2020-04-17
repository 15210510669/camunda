/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ColorPicker, Switch, Input} from 'components';
import ChartTargetInput from './subComponents/ChartTargetInput';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import {isDurationReport} from 'services';
import './BarChartConfig.scss';
import {t} from 'translation';

export default function BarChartConfig({onChange, report}) {
  const {
    combined,
    data: {visualization, configuration, groupBy},
    result,
  } = report;

  const durationReport = isDurationReport(combined ? Object.values(result.data)[0] : report);
  const combinedNumberReport = combined && visualization === 'number';

  return (
    <div className="BarChartConfig">
      {!combined &&
        (configuration.distributedBy === 'none' ||
          !['assignee', 'candidateGroup'].includes(groupBy.type)) && (
          <fieldset className="colorSection">
            <legend>{t('report.config.colorPicker.legend')}</legend>
            <ColorPicker
              selectedColor={configuration.color}
              onChange={(color) => onChange({color: {$set: color}})}
            />
          </fieldset>
        )}
      <fieldset>
        <legend>{t('report.config.tooltips.legend')}</legend>
        <RelativeAbsoluteSelection
          hideRelative={durationReport || combinedNumberReport}
          absolute={configuration.alwaysShowAbsolute}
          relative={configuration.alwaysShowRelative}
          onChange={(type, value) => {
            if (type === 'absolute') {
              onChange({alwaysShowAbsolute: {$set: value}});
            } else {
              onChange({alwaysShowRelative: {$set: value}});
            }
          }}
        />
      </fieldset>
      <fieldset>
        <legend>{t('report.config.axisLabels.legend')}</legend>
        <Input
          placeholder={t('report.config.axisLabels.xAxis')}
          type="text"
          value={configuration.xLabel}
          onChange={({target: {value}}) => onChange({xLabel: {$set: value}})}
        />
        <Input
          placeholder={t('report.config.axisLabels.yAxis')}
          type="text"
          value={configuration.yLabel}
          onChange={({target: {value}}) => onChange({yLabel: {$set: value}})}
        />
      </fieldset>
      <fieldset>
        <legend>
          <Switch
            checked={configuration.targetValue.active}
            onChange={({target: {checked}}) => onChange({targetValue: {active: {$set: checked}}})}
            label={t('report.config.goal.legend')}
          />
        </legend>
        <ChartTargetInput {...{onChange, report}} />
      </fieldset>
    </div>
  );
}
