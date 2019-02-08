import React, {Component} from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {Button, ShareEntity, ReportRenderer, Popover, Icon, ConfirmationModal} from 'components';
import {
  shareReport,
  revokeReportSharing,
  getSharedReport,
  remove,
  isSharingEnabled
} from './service';
import {checkDeleteConflict} from 'services';

export default class ReportView extends Component {
  state = {
    confirmModalVisible: false,
    conflict: null
  };

  async componentDidMount() {
    const sharingEnabled = await isSharingEnabled();

    this.setState({sharingEnabled});
  }

  shouldShowCSVDownload = () => {
    const {
      report: {result}
    } = this.props;
    return result;
  };

  showDeleteModal = async () => {
    let conflictState = {};
    const response = await checkDeleteConflict(this.props.report.id, 'report');
    if (response && response.conflictedItems && response.conflictedItems.length) {
      conflictState = {
        conflict: {
          type: 'Delete',
          items: response.conflictedItems
        }
      };
    }

    this.setState({
      confirmModalVisible: true,
      ...conflictState
    });
  };

  constructCSVDownloadLink = () => {
    const {excludedColumns} = this.props.report.data.configuration;

    const queryString = excludedColumns
      ? `?excludedColumns=${excludedColumns
          .map(column => column.replace('var__', 'variable:'))
          .join(',')}`
      : '';

    return `api/export/csv/${this.props.report.id}/${encodeURIComponent(
      this.props.report.name.replace(/\s/g, '_')
    )}.csv${queryString}`;
  };

  deleteReport = async evt => {
    await remove(this.props.report.id);

    this.setState({
      redirect: '/reports'
    });
  };

  closeConfirmModal = () => {
    this.setState({
      confirmModalVisible: false,
      conflict: null
    });
  };

  render() {
    const {confirmModalVisible, conflict, redirect, sharingEnabled} = this.state;
    const {report} = this.props;
    const {id, name, lastModifier, lastModified} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <>
        <ConfirmationModal
          open={confirmModalVisible}
          onClose={this.closeConfirmModal}
          onConfirm={this.deleteReport}
          conflict={conflict}
          entityName={name}
        />
        <div className="Report">
          <div className="Report__header">
            <div className="name-container">
              <h1 className="name">{name}</h1>
              <div className="metadata">
                Last modified {moment(lastModified).format('lll')} by {lastModifier}
              </div>
            </div>
            <div className="tools">
              <Link className="tool-button edit-button" to={`/report/${id}/edit`}>
                <Button>
                  <Icon type="edit" />
                  Edit
                </Button>
              </Link>
              <Button className="tool-button delete-button" onClick={this.showDeleteModal}>
                <Icon type="delete" />
                Delete
              </Button>
              <Popover
                className="tool-button share-button"
                icon="share"
                title="Share"
                tooltip={!sharingEnabled ? 'Sharing is disabled per configuration' : ''}
                disabled={!sharingEnabled}
              >
                <ShareEntity
                  type="report"
                  resourceId={id}
                  shareEntity={shareReport}
                  revokeEntitySharing={revokeReportSharing}
                  getSharedEntity={getSharedReport}
                />
              </Popover>
              {this.shouldShowCSVDownload() && (
                <a
                  className="Report__tool-button Report__csv-download-button"
                  href={this.constructCSVDownloadLink()}
                >
                  <Button>
                    <Icon type="save" />
                    Download CSV
                  </Button>
                </a>
              )}
            </div>
          </div>
          <div className="Report__view">
            <div className="Report__content">
              <ReportRenderer report={report} />
            </div>
          </div>
        </div>
      </>
    );
  }
}
