/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';

import {Button, DocsLink, EntityList, Icon, Tooltip} from 'components';
import {t} from 'translation';
import {withErrorHandling, withUser} from 'HOC';
import {addNotification, showError} from 'notifications';
import {getOptimizeProfile} from 'config';

import {DashboardView} from '../Dashboards/DashboardView';
import KpiResult from './KpiResult';
import KpiSummary from './KpiSummary';
import ConfigureProcessModal from './ConfigureProcessModal';
import KpiTooltip from './KpiTooltip';
import {loadProcesses, updateProcess, loadManagementDashboard} from './service';

import './Processes.scss';

export function Processes({mightFail, user}) {
  const [processes, setProcesses] = useState();
  const [sorting, setSorting] = useState();
  const [editProcessConfig, setEditProcessConfig] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [dashboard, setDashboard] = useState();

  useEffect(() => {
    mightFail(loadManagementDashboard(), setDashboard, showError);
  }, [mightFail]);

  const loadProcessesList = useCallback(
    (sortBy, sortOrder) => {
      setSorting({key: sortBy, order: sortOrder});
      mightFail(loadProcesses(sortBy, sortOrder), setProcesses, showError);
    },
    [mightFail]
  );

  useEffect(() => {
    loadProcessesList();
  }, [loadProcessesList]);

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  const columns = [
    t('common.name'),
    <>
      {t('processes.timeKpi')} <KpiTooltip />
    </>,
    <>
      {t('processes.qualityKpi')} <KpiTooltip />
    </>,
    t('common.configure'),
  ];

  if (optimizeProfile === 'cloud' || optimizeProfile === 'platform') {
    const ownerColumn = t('processes.owner');
    columns.splice(1, 0, ownerColumn);
  }

  const isEditor = user?.authorizations.includes('entity_editor');
  if (isEditor) {
    columns.splice(-1, 0, t('dashboard.label'));
  }

  const processesLabel =
    processes?.length === 1 ? t('processes.label') : t('processes.label-plural');

  return (
    <div className="Processes">
      <h1 className="processOverview">
        {t('processes.processOverview')}
        {processes && (
          <div className="info">
            <span
              dangerouslySetInnerHTML={{
                __html: t('processes.analysing', {count: processes.length, label: processesLabel}),
              }}
            />{' '}
            <DocsLink location="components/optimize/userguide/processes">
              {t('events.sources.learnMore')}
            </DocsLink>
          </div>
        )}
      </h1>
      {dashboard && (
        <DashboardView
          reports={dashboard.reports}
          availableFilters={dashboard.availableFilters}
          customizeReportLink={(id) => `/processes/report/${id}/`}
          simplifiedDateFilter
        />
      )}
      <EntityList
        name={t('processes.list')}
        displaySearchInfo={
          processes &&
          ((query, count) => (
            <div className="info">
              {query
                ? t('processes.processesListedOf', {
                    count,
                    total: processes.length,
                    label: processesLabel,
                  })
                : t('processes.processesListed', {total: processes.length, label: processesLabel})}
            </div>
          ))
        }
        empty={t('processes.empty')}
        isLoading={!processes}
        columns={columns}
        sorting={sorting}
        onChange={loadProcessesList}
        data={processes?.map(
          ({processDefinitionKey, processDefinitionName, owner, digest, kpis, linkToDashboard}) => {
            const kpisWithData = kpis.filter(({value, target}) => value && target);
            const timeKpis = kpisWithData?.filter((kpi) => kpi.type === 'time');
            const qualityKpis = kpisWithData?.filter((kpi) => kpi.type === 'quality');
            const meta = [
              <Tooltip position="bottom" content={<KpiResult kpis={timeKpis} />} delay={300}>
                <div className="summaryContainer">
                  <KpiSummary kpis={timeKpis} />
                </div>
              </Tooltip>,
              <Tooltip position="bottom" content={<KpiResult kpis={qualityKpis} />} delay={300}>
                <div className="summaryContainer">
                  <KpiSummary kpis={qualityKpis} />
                </div>
              </Tooltip>,
            ];

            if (isEditor) {
              meta.push(
                <Link className="processHoverBtn" to={linkToDashboard} target="_blank">
                  {t('common.view')} <Icon type="jump" />
                </Link>
              );
            }

            if (optimizeProfile === 'cloud' || optimizeProfile === 'platform') {
              meta.unshift(owner?.name);

              meta.push(
                <Button
                  className="processHoverBtn"
                  onClick={() => setEditProcessConfig({processDefinitionKey, owner, digest})}
                >
                  {t('common.configure')}
                </Button>
              );
            }

            return {
              id: processDefinitionKey,
              type: t('common.process.label'),
              icon: 'data-source',
              name: processDefinitionName || processDefinitionKey,
              meta,
            };
          }
        )}
      />
      {editProcessConfig && (
        <ConfigureProcessModal
          initialConfig={editProcessConfig}
          onClose={() => setEditProcessConfig()}
          onConfirm={(newConfig, emailEnabled, ownerName) => {
            setEditProcessConfig();
            mightFail(
              updateProcess(editProcessConfig.processDefinitionKey, newConfig),
              () => {
                if (emailEnabled && newConfig.processDigest.enabled) {
                  addNotification({
                    type: 'success',
                    text: t('processes.digestConfigured', {name: ownerName}),
                  });
                }
                loadProcessesList();
              },
              showError
            );
          }}
        />
      )}
    </div>
  );
}

export default withUser(withErrorHandling(Processes));
