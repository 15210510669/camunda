/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useRef} from 'react';
import classnames from 'classnames';
import {FullScreen, useFullScreenHandle} from 'react-full-screen';
import {Link, useHistory} from 'react-router-dom';

import {
  Button,
  ShareEntity,
  DashboardRenderer,
  LastModifiedInfo,
  Icon,
  Popover,
  Deleter,
  EntityName,
  DiagramScrollLock,
  AlertsDropdown,
  EntityDescription,
  DashboardTemplateModal,
} from 'components';
import {evaluateReport, createEntity, deleteEntity, addSources} from 'services';
import {themed} from 'theme';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import {
  getSharedDashboard,
  shareDashboard,
  revokeDashboardSharing,
  getDefaultFilter,
} from './service';
import {FiltersView} from './filters';
import {AutoRefreshBehavior, AutoRefreshSelect} from './AutoRefresh';
import useReportDefinitions from './useReportDefinitions';

import './DashboardView.scss';

export function DashboardView(props) {
  const {
    id,
    name,
    description,
    currentUserRole,
    isAuthorizedToShare,
    isInstantDashboard,
    sharingEnabled,
    tiles,
    availableFilters,
    theme,
    toggleTheme,
    lastModified,
    lastModifier,
    owner,
    loadDashboard,
    onDelete,
    refreshRateSeconds,
    disableNameLink,
    customizeReportLink,
    simplifiedDateFilter,
  } = props;
  const [autoRefreshInterval, setAutoRefreshInterval] = useState(refreshRateSeconds * 1000);
  const [deleting, setDeleting] = useState(null);
  const [filtersShown, setFiltersShown] = useState(availableFilters?.length > 0);
  const [filter, setFilter] = useState(getDefaultFilter(availableFilters));
  const fullScreenHandle = useFullScreenHandle();
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);

  const optimizeReports = tiles?.filter(({id, report}) => !!id || !!report);
  const {definitions} = useReportDefinitions(optimizeReports?.[0]);
  const {mightFail} = useErrorHandling();
  const history = useHistory();

  const themeRef = useRef(theme);

  // we need to store the theme in a ref in order to access the latest state
  // in the componentDidUnmount effect below
  useEffect(() => {
    themeRef.current = theme;
  }, [theme]);

  useEffect(
    () => () => {
      if (themeRef.current === 'dark') {
        toggleTheme();
      }
    },
    [toggleTheme]
  );

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  function changeFullScreen() {
    if (theme === 'dark') {
      toggleTheme();
    }
  }

  function getShareTooltip() {
    if (!sharingEnabled) {
      return t('common.sharing.disabled');
    }
    if (!isAuthorizedToShare) {
      return t('dashboard.cannotShare');
    }
    return '';
  }

  async function handleInstantPreviewDashboardCopying(dashboardState) {
    const {definitions} = dashboardState;
    const [definition] = definitions || [];
    const {key: definitionKey, tenantIds: tenants} = definition || {};
    let collectionId;

    mightFail(
      (async () => {
        collectionId = await createEntity('collection', {name: definitionKey});
        await addSources(collectionId, [
          {
            definitionKey,
            definitionType: 'process',
            tenants,
          },
        ]);
      })(),
      () =>
        history.push({
          pathname: '/collection/' + collectionId + '/dashboard/new/edit',
          state: dashboardState,
        }),
      (error) => {
        if (collectionId) {
          deleteEntity('collection', collectionId);
        }
        showError(error);
      }
    );
  }

  return (
    <FullScreen handle={fullScreenHandle} onChange={changeFullScreen}>
      <div
        className={classnames('DashboardView', {
          fullscreen: fullScreenHandle.active,
        })}
      >
        <div className="header">
          <div className="head">
            <div className="info">
              <EntityName
                details={<LastModifiedInfo entity={{lastModified, lastModifier, owner}} />}
              >
                {name}
              </EntityName>
              {description && <EntityDescription description={description} />}
            </div>
            <div className="tools">
              {!fullScreenHandle.active && (
                <React.Fragment>
                  {isInstantDashboard && (
                    <Button
                      onClick={() => setIsTemplateModalOpen(true)}
                      main
                      primary
                      className="tool-button create-copy"
                    >
                      {t('dashboard.copyInstantDashboard')}
                    </Button>
                  )}
                  {currentUserRole === 'editor' && (
                    <>
                      <Link className="tool-button edit-button" to="edit">
                        <Button main tabIndex="-1">
                          <Icon type="edit" />
                          {t('common.edit')}
                        </Button>
                      </Link>
                      <Button
                        main
                        onClick={() => setDeleting({...props, entityType: 'dashboard'})}
                        className="tool-button delete-button"
                      >
                        <Icon type="delete" />
                        {t('common.delete')}
                      </Button>
                    </>
                  )}
                  {!isInstantDashboard && (
                    <Popover
                      main
                      className="tool-button share-button"
                      icon="share"
                      title={t('common.sharing.buttonTitle')}
                      disabled={!sharingEnabled || !isAuthorizedToShare}
                      tooltip={getShareTooltip()}
                    >
                      <ShareEntity
                        type="dashboard"
                        resourceId={id}
                        shareEntity={shareDashboard}
                        revokeEntitySharing={revokeDashboardSharing}
                        getSharedEntity={getSharedDashboard}
                        filter={filter}
                        defaultFilter={getDefaultFilter(availableFilters)}
                      />
                    </Popover>
                  )}
                </React.Fragment>
              )}
              {fullScreenHandle.active && (
                <Button main onClick={toggleTheme} className="tool-button theme-toggle">
                  {t('dashboard.toggleTheme')}
                </Button>
              )}
              {availableFilters?.length > 0 && (
                <Button
                  main
                  className="tool-button filter-button"
                  active={filtersShown}
                  onClick={() => {
                    if (filtersShown) {
                      setFiltersShown(false);
                      setFilter([]);
                    } else {
                      setFiltersShown(true);
                      setFilter(getDefaultFilter(availableFilters));
                    }
                  }}
                >
                  <Icon type="filter" /> {t('dashboard.filter.viewButtonText')}
                </Button>
              )}
              {!fullScreenHandle.active &&
                (optimizeProfile === 'cloud' || optimizeProfile === 'platform') && (
                  <AlertsDropdown dashboardTiles={tiles} />
                )}
              <Button
                main
                onClick={() =>
                  fullScreenHandle.active ? fullScreenHandle.exit() : fullScreenHandle.enter()
                }
                className="tool-button fullscreen-button"
              >
                <Icon type={fullScreenHandle.active ? 'exit-fullscreen' : 'fullscreen'} />{' '}
                {fullScreenHandle.active
                  ? t('dashboard.leaveFullscreen')
                  : t('dashboard.enterFullscreen')}
              </Button>
              <AutoRefreshSelect
                refreshRateMs={autoRefreshInterval}
                onChange={setAutoRefreshInterval}
                onRefresh={loadDashboard}
              />
            </div>
          </div>
        </div>
        {filtersShown && (
          <FiltersView
            reports={tiles}
            availableFilters={availableFilters}
            filter={filter}
            setFilter={setFilter}
            simplifiedDateFilter={simplifiedDateFilter}
          />
        )}
        <Deleter
          type="dashboard"
          entity={deleting}
          onDelete={onDelete}
          onClose={() => setDeleting(null)}
        />
        <div className="content">
          <DashboardRenderer
            loadTile={evaluateReport}
            tiles={tiles}
            filter={filter}
            disableNameLink={disableNameLink}
            customizeTileLink={customizeReportLink}
            addons={[
              <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />,
              <DiagramScrollLock key="diagramScrollLock" />,
            ]}
          />
        </div>
      </div>
      {isInstantDashboard && isTemplateModalOpen && (
        <DashboardTemplateModal
          initialDefinitions={definitions}
          onClose={() => setIsTemplateModalOpen(false)}
          onConfirm={handleInstantPreviewDashboardCopying}
        />
      )}
    </FullScreen>
  );
}

export default themed(DashboardView);
