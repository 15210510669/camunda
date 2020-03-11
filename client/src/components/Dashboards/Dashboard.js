/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import moment from 'moment';

import {withErrorHandling} from 'HOC';
import {loadEntity, updateEntity, createEntity, getCollection} from 'services';
import {isSharingEnabled} from 'config';

import {ErrorPage, LoadingIndicator} from 'components';

import {addNotification, showError} from 'notifications';
import {t} from 'translation';

import {isAuthorizedToShareDashboard} from './service';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import './Dashboard.scss';

export default withErrorHandling(
  class Dashboard extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        name: null,
        lastModified: null,
        lastModifier: null,
        currentUserRole: null,
        loaded: false,
        redirect: '',
        reports: [],
        serverError: null,
        isAuthorizedToShare: false,
        sharingEnabled: false
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

    createDashboard = () => {
      this.setState({
        loaded: true,
        name: t('dashboard.new'),
        lastModified: getFormattedNowDate(),
        lastModifier: t('common.you'),
        currentUserRole: 'editor',
        reports: [],
        isAuthorizedToShare: true
      });
    };

    loadDashboard = () => {
      this.props.mightFail(
        loadEntity('dashboard', this.getId()),
        async response => {
          const {name, lastModifier, currentUserRole, lastModified, reports} = response;

          this.setState({
            lastModifier,
            lastModified,
            currentUserRole,
            loaded: true,
            name,
            reports: reports || [],
            isAuthorizedToShare: await isAuthorizedToShareDashboard(this.getId())
          });
        },
        err => {
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
        redirect: '../../'
      });
    };

    updateDashboard = (id, name, reports) => {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          updateEntity('dashboard', id, {
            name,
            reports
          }),
          () => resolve(this.updateDashboardState(id, name, reports)),
          () => reject(addNotification({text: t('dashboard.cannotSave', {name}), type: 'error'}))
        );
      });
    };

    updateDashboardState = async (id, name, reports) => {
      this.setState({
        name,
        reports,
        redirect: this.isNew() ? `../${id}/` : './',
        isAuthorizedToShare: await isAuthorizedToShareDashboard(id),
        lastModified: getFormattedNowDate()
      });
    };

    saveChanges = (name, reports) => {
      return new Promise(async (resolve, reject) => {
        if (this.isNew()) {
          const collectionId = getCollection(this.props.location.pathname);

          this.props.mightFail(
            createEntity('dashboard', {collectionId, name, reports}),
            id => resolve(this.updateDashboardState(id, name, reports)),
            () => reject(addNotification({text: t('dashboard.cannotSave', {name}), type: 'error'}))
          );
        } else {
          resolve(this.updateDashboard(this.getId(), name, reports));
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
        sharingEnabled,
        isAuthorizedToShare,
        reports
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
        lastModified,
        lastModifier,
        id: this.getId()
      };

      return (
        <div className="Dashboard">
          {viewMode === 'edit' ? (
            <DashboardEdit
              {...commonProps}
              isNew={this.isNew()}
              saveChanges={this.saveChanges}
              initialReports={this.state.reports}
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
            />
          )}
        </div>
      );
    }
  }
);

function getFormattedNowDate() {
  return moment().format('Y-MM-DDTHH:mm:ss.SSSZZ');
}
