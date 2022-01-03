/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {loadEntity, updateEntity, createEntity, getCollection} from 'services';
import {isSharingEnabled, newReport} from 'config';

import {ErrorPage, LoadingIndicator} from 'components';

import {showError} from 'notifications';
import {t} from 'translation';

import {isAuthorizedToShareDashboard} from './service';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import './Dashboard.scss';

export class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      name: null,
      lastModified: null,
      lastModifier: null,
      owner: null,
      currentUserRole: null,
      loaded: false,
      redirect: '',
      reports: [],
      availableFilters: [],
      serverError: null,
      isAuthorizedToShare: false,
      sharingEnabled: false,
      refreshRateSeconds: null,
    };
  }

  getId = () => this.props.match.params.id;
  isNew = () => this.getId() === 'new';

  componentDidMount = async () => {
    this.setState({sharingEnabled: await isSharingEnabled()});

    if (this.isNew()) {
      this.createDashboard();
    } else {
      this.loadDashboard();
    }
  };

  createDashboard = async () => {
    const user = await this.props.getUser();

    const initialData = this.props.location.state;

    const modifierData = {
      lastModified: getFormattedNowDate(),
      lastModifier: user.name,
      owner: user.name,
    };

    this.setState({
      loaded: true,
      name: initialData?.name ?? t('dashboard.new'),
      ...modifierData,
      currentUserRole: 'editor',
      reports:
        initialData?.data?.map((config) => {
          return {
            ...config,
            report: {
              ...newReport.new,
              ...modifierData,
              name: config.report.name,
              data: {
                ...newReport.new.data,
                ...config.report.data,
                definitions: initialData.definitions,
                configuration: {
                  ...newReport.new.data.configuration,
                  ...(config.report.data?.configuration ?? {}),
                  xml: initialData.xml,
                },
              },
            },
          };
        }) ?? [],
      availableFilters: [],
      isAuthorizedToShare: true,
      refreshRateSeconds: null,
    });
  };

  loadDashboard = () => {
    this.props.mightFail(
      loadEntity('dashboard', this.getId()),
      async (response) => {
        const {
          name,
          lastModifier,
          currentUserRole,
          lastModified,
          owner,
          reports,
          availableFilters,
          refreshRateSeconds,
        } = response;

        this.setState({
          lastModifier,
          lastModified,
          owner,
          currentUserRole,
          loaded: true,
          name,
          reports: reports || [],
          availableFilters: availableFilters || [],
          isAuthorizedToShare: await isAuthorizedToShareDashboard(this.getId()),
          refreshRateSeconds,
        });
      },
      (err) => {
        if (!this.state.loaded) {
          this.setState({serverError: err.status});
        } else {
          showError(err);
        }
      }
    );
  };

  goHome = () => {
    this.setState({
      redirect: '../../',
    });
  };

  updateDashboard = (id, name, reports, availableFilters, refreshRateSeconds, stayInEditMode) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity('dashboard', id, {
          name,
          reports,
          availableFilters,
          refreshRateSeconds,
        }),
        () =>
          resolve(
            this.updateDashboardState(
              id,
              name,
              reports,
              availableFilters,
              refreshRateSeconds,
              stayInEditMode
            )
          ),
        (error) => reject(showError(error))
      );
    });
  };

  updateDashboardState = async (
    id,
    name,
    reports,
    availableFilters,
    refreshRateSeconds,
    stayInEditMode
  ) => {
    const user = await this.props.getUser();
    const redirect = this.isNew() ? `../${id}/` : './';

    const update = {
      name,
      reports,
      availableFilters,
      isAuthorizedToShare: await isAuthorizedToShareDashboard(id),
      lastModified: getFormattedNowDate(),
      lastModifier: user.name,
      refreshRateSeconds,
    };

    if (stayInEditMode) {
      this.props.history.replace(redirect + 'edit');
    } else {
      update.redirect = redirect;
    }

    this.setState(update);
  };

  saveChanges = (name, reports, availableFilters, refreshRateSeconds, stayInEditMode) => {
    return new Promise(async (resolve, reject) => {
      if (this.isNew()) {
        const collectionId = getCollection(this.props.location.pathname);

        const reportIds = await Promise.all(
          reports.map((report) => {
            return (
              report.id ||
              (report.report &&
                new Promise((resolve, reject) => {
                  const {name, data, reportType, combined} = report.report;
                  const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;
                  this.props.mightFail(
                    createEntity(endpoint, {collectionId, name, data}),
                    resolve,
                    reject
                  );
                })) ||
              ''
            );
          })
        );

        const savedReports = reports.map(({configuration, dimensions, position}, idx) => {
          return {
            configuration,
            dimensions,
            position,
            id: reportIds[idx],
          };
        });

        this.props.mightFail(
          createEntity('dashboard', {
            collectionId,
            name,
            reports: savedReports,
            availableFilters,
            refreshRateSeconds,
          }),
          (id) =>
            resolve(
              this.updateDashboardState(
                id,
                name,
                savedReports,
                availableFilters,
                refreshRateSeconds,
                stayInEditMode
              )
            ),
          (error) => reject(showError(error))
        );
      } else {
        resolve(
          this.updateDashboard(
            this.getId(),
            name,
            reports,
            availableFilters,
            refreshRateSeconds,
            stayInEditMode
          )
        );
      }
    });
  };

  componentDidUpdate() {
    if (this.state.redirect) {
      this.setState({redirect: ''});
    }
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {
      loaded,
      redirect,
      serverError,
      name,
      lastModified,
      currentUserRole,
      lastModifier,
      owner,
      sharingEnabled,
      isAuthorizedToShare,
      reports,
      availableFilters,
      refreshRateSeconds,
    } = this.state;

    if (serverError) {
      return <ErrorPage />;
    }

    if (!loaded) {
      return <LoadingIndicator />;
    }

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    const commonProps = {
      name,
      refreshRateSeconds,
      lastModified,
      lastModifier,
      owner,
      id: this.getId(),
    };

    return (
      <div className="Dashboard">
        {viewMode === 'edit' ? (
          <DashboardEdit
            {...commonProps}
            isNew={this.isNew()}
            saveChanges={this.saveChanges}
            initialReports={reports}
            initialAvailableFilters={availableFilters}
          />
        ) : (
          <DashboardView
            {...commonProps}
            sharingEnabled={sharingEnabled}
            isAuthorizedToShare={isAuthorizedToShare}
            loadDashboard={this.loadDashboard}
            onDelete={this.goHome}
            currentUserRole={currentUserRole}
            reports={reports}
            availableFilters={availableFilters}
          />
        )}
      </div>
    );
  }
}

export default withErrorHandling(withUser(Dashboard));

function getFormattedNowDate() {
  return format(new Date(), BACKEND_DATE_FORMAT);
}
