/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import classnames from 'classnames';
import update from 'immutability-helper';

import {Select, Button, Icon} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {loadVariables, reportConfig} from 'services';

export function DistributedBy({report, onChange, mightFail}) {
  const {definitions, distributedBy, view, groupBy, visualization} = report.data;
  const {key, versions, tenantIds} = definitions?.[0] ?? {};
  const [variables, setVariables] = useState([]);

  useEffect(() => {
    if (isInstanceDateReport(view, groupBy)) {
      mightFail(
        loadVariables([
          {
            processDefinitionKey: key,
            processDefinitionVersions: versions,
            tenantIds,
          },
        ]),
        setVariables,
        showError
      );
    }
  }, [view, groupBy, mightFail, key, versions, tenantIds, setVariables]);

  if (canDistributeData(view, groupBy)) {
    const value = getValue(distributedBy);
    const hasDistribution = value !== 'none';

    return (
      <li className="DistributedBy GroupBy">
        <span className="label">{t('common.and')}</span>
        <Select
          className={classnames('ReportSelect', {hasNoGrouping: !hasDistribution})}
          key={variables.length}
          value={value}
          label={!hasDistribution && '+ Add grouping'}
          onChange={(value) => {
            const change = {distributedBy: {$set: {type: value, value: null}}};

            if (isInstanceDateReport(view, groupBy) && value !== 'none') {
              const variable = variables.find(({name}) => name === value);
              change.distributedBy.$set = {type: 'variable', value: variable};
            }

            if (isInstanceVariableReport(view, groupBy) && value !== 'none') {
              const [type, unit] = value.split('_');
              change.distributedBy.$set = {type, value: {unit}};
            }

            if (
              !reportConfig['process'].isAllowed(
                update(report, {data: change}),
                view,
                groupBy,
                visualization
              )
            ) {
              change.visualization = {$set: 'bar'};
            }

            onChange(change, true);
          }}
        >
          {getOptionsFor(view.entity, groupBy.type, variables)}
        </Select>
        {hasDistribution && (
          <Button
            className="removeGrouping"
            onClick={() => {
              const change = {distributedBy: {$set: {type: 'none', value: null}}};
              if (visualization === 'stacked') {
                change.visualization = {$set: 'bar'};
              }
              onChange(change, true);
            }}
          >
            <Icon type="close-small" />
          </Button>
        )}
      </li>
    );
  }
  return null;
}

function getValue(distributedBy) {
  if (distributedBy.type === 'variable') {
    return distributedBy.value.name;
  }

  if (['startDate', 'endDate'].includes(distributedBy.type)) {
    const {value, type} = distributedBy;
    return type + '_' + value.unit;
  }

  return distributedBy.type;
}

function canDistributeData(view, groupBy) {
  if (!view || !groupBy) {
    return false;
  }

  if (view.entity === 'userTask') {
    return true;
  }

  if (
    view.entity === 'flowNode' &&
    ['startDate', 'endDate', 'duration', 'variable'].includes(groupBy.type)
  ) {
    return true;
  }

  if (isInstanceDateReport(view, groupBy)) {
    return true;
  }

  if (view.entity === 'processInstance' && groupBy.type === 'variable') {
    return true;
  }
}

function getOptionsFor(view, groupBy, variables) {
  const options = [];

  if (view === 'userTask') {
    if (['userTasks', 'startDate', 'endDate'].includes(groupBy)) {
      options.push(
        <Select.Option key="assignee" value="assignee">
          {t('report.groupBy.userAssignee')}
        </Select.Option>,
        <Select.Option key="candidateGroup" value="candidateGroup">
          {t('report.groupBy.userGroup')}
        </Select.Option>
      );
    }

    if (groupBy !== 'userTasks') {
      options.push(
        <Select.Option key="userTask" value="userTask">
          {t('report.view.userTask')}
        </Select.Option>
      );
    }
  }

  if (view === 'flowNode') {
    if (['startDate', 'endDate', 'duration', 'variable'].includes(groupBy)) {
      options.push(
        <Select.Option key="flowNode" value="flowNode">
          {t('report.view.fn')}
        </Select.Option>
      );
    }
  }

  if (view === 'processInstance') {
    if (groupBy === 'startDate' || groupBy === 'endDate') {
      options.push(
        <Select.Submenu key="variable" label="Variable">
          {variables.map(({name}, idx) => {
            return (
              <Select.Option key={idx} value={name}>
                {name}
              </Select.Option>
            );
          })}
        </Select.Submenu>
      );
    }

    if (groupBy === 'variable') {
      ['startDate', 'endDate'].map((key) =>
        options.push(
          <Select.Submenu key={key} label={t('report.groupBy.' + key)}>
            <Select.Option value={key + '_automatic'}>{t('common.unit.automatic')}</Select.Option>
            <Select.Option value={key + '_hour'}>
              {t('common.unit.hour.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_day'}>{t('common.unit.day.label-plural')}</Select.Option>
            <Select.Option value={key + '_week'}>
              {t('common.unit.week.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_month'}>
              {t('common.unit.month.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_year'}>
              {t('common.unit.year.label-plural')}
            </Select.Option>
          </Select.Submenu>
        )
      );
    }
  }

  return options;
}

function isInstanceDateReport(view, groupBy) {
  return (
    view?.entity === 'processInstance' &&
    (groupBy?.type === 'startDate' || groupBy?.type === 'endDate')
  );
}

function isInstanceVariableReport(view, groupBy) {
  return view?.entity === 'processInstance' && groupBy?.type === 'variable';
}

export default withErrorHandling(DistributedBy);
