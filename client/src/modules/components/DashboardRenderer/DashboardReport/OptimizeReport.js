/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ReportRenderer, LoadingIndicator, NoDataNotice} from 'components';
import {Link, withRouter} from 'react-router-dom';
import {withErrorHandling} from 'HOC';

import {themed} from 'theme';

import './OptimizeReport.scss';
import {t} from 'translation';

export default themed(
  withErrorHandling(
    withRouter(
      class OptimizeReport extends React.Component {
        constructor(props) {
          super(props);

          this.state = {
            loading: true,
            data: undefined,
            error: null,
          };
        }

        async componentDidMount() {
          await this.loadReport();
        }

        componentDidUpdate(prevProps) {
          if (prevProps.report !== this.props.report || prevProps.filter !== this.props.filter) {
            this.loadReport();
          }
        }

        loadReport = async () => {
          this.setState({loading: true});
          await this.props.mightFail(
            this.props.loadReport(this.props.report.id, this.props.filter),
            (response) => {
              this.setState({
                loading: false,
                data: response,
              });
            },
            async (e) => {
              const errorData = await e.json();
              this.setState({
                loading: false,
                data: errorData.reportDefinition,
                error: formatError(e, errorData),
              });
            }
          );
        };

        getName = () => {
          if (this.state.data) {
            return this.state.data.name;
          }
        };

        exitDarkmode = () => {
          if (this.props.theme === 'dark') {
            this.props.toggleTheme();
          }
        };

        render() {
          const {loading, data, error} = this.state;

          if (loading) {
            return <LoadingIndicator />;
          }

          const {report, disableNameLink, children = () => {}} = this.props;

          const reportName = this.getName();

          return (
            <div className="DashboardReport__wrapper">
              <div className="OptimizeReport__header">
                {disableNameLink ? (
                  <span className="OptimizeReport__heading" title={reportName}>
                    {reportName}
                  </span>
                ) : (
                  <Link
                    to={`${this.props.location.pathname}report/${report.id}/`}
                    onClick={this.exitDarkmode}
                    className="OptimizeReport__heading"
                    title={reportName}
                  >
                    {reportName}
                  </Link>
                )}
              </div>
              <div className="OptimizeReport__visualization">
                {error ? (
                  <NoDataNotice title={error.title}>{error.text}</NoDataNotice>
                ) : (
                  <ReportRenderer report={data} context="dashboard" />
                )}
              </div>
              {children({loadReportData: this.loadReport})}
            </div>
          );
        }
      }
    )
  )
);

function formatError(e, {errorCode, errorMessage}) {
  if (e.status === 403) {
    return {
      title: t('dashboard.noAuthorization'),
      text: t('dashboard.noReportAccess'),
    };
  }

  return {
    text: errorCode ? t('apiErrors.' + errorCode) : errorMessage,
  };
}
