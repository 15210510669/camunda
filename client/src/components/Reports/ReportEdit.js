/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import deepEqual from 'deep-equal';
import {Redirect, withRouter} from 'react-router-dom';

import {withErrorHandling} from 'HOC';
import {nowDirty, nowPristine} from 'saveGuard';
import {
  ReportRenderer,
  LoadingIndicator,
  MessageBox,
  EntityNameForm,
  InstanceCount,
} from 'components';
import {
  incompatibleFilters,
  containsSuspensionFilter,
  updateEntity,
  createEntity,
  evaluateReport,
  getCollection,
} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {withDocs} from 'HOC';

import ReportControlPanel from './controlPanels/ReportControlPanel';
import DecisionControlPanel from './controlPanels/DecisionControlPanel';
import CombinedReportPanel from './controlPanels/CombinedReportPanel';
import ConflictModal from './ConflictModal';

export class ReportEdit extends React.Component {
  state = {
    loadingReportData: false,
    redirect: '',
    conflict: null,
    originalData: this.props.report,
    updatePromise: null,
    optimizeVersion: 'latest',
    report: this.props.report,
  };

  async componentDidMount() {
    const {report} = this.state;

    if (this.isReportComplete(report) && !report.result) {
      this.loadReport(report);
      nowDirty(t('report.label'), this.save);
    }
  }

  showSaveError = (error) => {
    this.setState({
      conflict: null,
    });
    showError(error);
  };

  saveUpdatedReport = ({endpoint, id, name, data}) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity(endpoint, id, {name, data}, {query: {force: this.state.conflict !== null}}),
        () => resolve(id),
        async (error) => {
          if (error.status === 409) {
            const {conflictedItems} = await error.json();
            this.setState({
              report: update(this.state.report, {name: {$set: name}}),
              conflict: conflictedItems.reduce(
                (obj, conflict) => {
                  obj[conflict.type].push(conflict);
                  return obj;
                },
                {alert: [], combined_report: []}
              ),
              updatePromise: resolve,
            });
          } else {
            reject(this.showSaveError(error));
          }
        }
      );
    });
  };

  save = () => {
    return new Promise(async (resolve, reject) => {
      const {id, name, data, reportType, combined} = this.state.report;
      const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;

      if (this.props.isNew) {
        const collectionId = getCollection(this.props.location.pathname);

        this.props.mightFail(createEntity(endpoint, {collectionId, name, data}), resolve, (error) =>
          reject(this.showSaveError(error))
        );
      } else {
        resolve(await this.saveUpdatedReport({endpoint, id, name, data}));
      }
    });
  };

  saveAndGoBack = async () => {
    const id = await this.save();
    if (this.state.updatePromise) {
      this.state.updatePromise(id);
      this.setState({updatePromise: null});
    }
    if (id) {
      nowPristine();
      this.props.updateOverview(update(this.state.report, {id: {$set: id}}));
      this.setState({redirect: this.props.isNew ? `../${id}/` : './'});
    }
  };

  cancel = () => {
    nowPristine();
    this.setState({
      report: this.state.originalData,
    });
  };

  updateReport = async (change, needsReevaluation) => {
    const newReport = update(this.state.report.data, change);

    this.setState(
      ({report}) => ({
        report: update(report, {data: change}),
      }),
      this.dirtyCheck
    );

    if (needsReevaluation) {
      const query = {
        ...this.state.report,
        data: newReport,
      };
      delete query.result;
      await this.loadReport(query);
    }
  };

  updateName = ({target: {value}}) => {
    this.setState(
      ({report}) => ({
        report: update(report, {name: {$set: value}}),
      }),
      this.dirtyCheck
    );
  };

  dirtyCheck = () => {
    if (deepEqual(this.state.report, this.state.originalData)) {
      nowPristine();
    } else {
      nowDirty(t('report.label'), this.save);
    }
  };

  isReportComplete = ({data: {view, groupBy, visualization}, combined}) =>
    (view && groupBy && visualization) || combined;

  loadReport = async (query) => {
    this.setState({report: query});

    if (this.isReportComplete(query)) {
      this.setState({loadingReportData: true});
      await this.props.mightFail(
        evaluateReport(query),
        (response) =>
          this.setState({
            report: response,
          }),
        showError
      );
      this.setState({loadingReportData: false});
    }
  };

  showIncompleteResultWarning = () => {
    const {report} = this.state;
    if (
      !report ||
      !report.result ||
      typeof report.result.isComplete === 'undefined' ||
      !report.data.visualization
    ) {
      return false;
    }

    return !report.result.isComplete;
  };

  closeConflictModal = () => {
    this.state.updatePromise(null);
    this.setState({conflict: null, updatePromise: null});
  };

  render() {
    const {report, loadingReportData, conflict, redirect} = this.state;
    const {name, data, combined, reportType} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="Report">
        <div className="Report__header">
          <EntityNameForm
            name={name}
            entity="Report"
            isNew={this.props.isNew}
            onChange={this.updateName}
            onSave={this.saveAndGoBack}
            onCancel={this.cancel}
          />
          <InstanceCount noInfo report={report} />
        </div>

        {!combined && reportType === 'process' && (
          <ReportControlPanel report={report} updateReport={this.updateReport} />
        )}

        {!combined && reportType === 'decision' && (
          <DecisionControlPanel report={report} updateReport={this.updateReport} />
        )}

        {this.showIncompleteResultWarning() && (
          <MessageBox type="warning">
            {t('report.incomplete', {
              count: report.result.data.length || Object.keys(report.result.data).length,
            })}
          </MessageBox>
        )}

        {data?.filter && incompatibleFilters(data.filter) && (
          <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
        )}

        {data?.filter && containsSuspensionFilter(data.filter) && (
          <MessageBox
            type="warning"
            dangerouslySetInnerHTML={{
              __html: t('common.filter.suspensionFilterWarning', {
                docsLink:
                  this.props.docsLink + 'technical-guide/update/2.7-to-3.0/#suspension-filter',
              }),
            }}
          />
        )}

        {data?.groupBy?.type === 'endDate' &&
          data.configuration.flowNodeExecutionState === 'running' && (
            <MessageBox type="warning">{t('report.runningEndedUserTaskWarning')}</MessageBox>
          )}

        <div className="Report__view">
          <div className="Report__content">
            {loadingReportData ? (
              <LoadingIndicator />
            ) : (
              <ReportRenderer report={report} updateReport={this.updateReport} />
            )}
          </div>
          {combined && <CombinedReportPanel report={report} updateReport={this.updateReport} />}
        </div>
        <ConflictModal
          conflict={conflict}
          onClose={this.closeConflictModal}
          onConfirm={this.saveAndGoBack}
        />
      </div>
    );
  }
}

export default withRouter(withErrorHandling(withDocs(ReportEdit)));
