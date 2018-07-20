import React from 'react';
import moment from 'moment';
import classnames from 'classnames';
import Fullscreen from 'react-full-screen';
import {default as updateState} from 'immutability-helper';
import {Link, Redirect} from 'react-router-dom';
import {withErrorHandling} from 'HOC';

import {
  Button,
  Modal,
  Input,
  ShareEntity,
  DashboardView,
  Icon,
  Dropdown,
  Popover,
  ErrorMessage,
  ErrorPage
} from 'components';

import {
  loadDashboard,
  remove,
  update,
  loadReport,
  getSharedDashboard,
  shareDashboard,
  revokeDashboardSharing,
  isAuthorizedToShareDashboard
} from './service';

import {AddButton} from './AddButton';
import {Grid} from './Grid';
import {DimensionSetter} from './DimensionSetter';
import {DeleteButton} from './DeleteButton';
import {DragBehavior} from './DragBehavior';
import {ResizeHandle} from './ResizeHandle';
import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './Dashboard.css';

export default withErrorHandling(
  class Dashboard extends React.Component {
    constructor(props) {
      super(props);

      this.id = props.match.params.id;
      this.isNew = this.props.location.search === '?new';

      this.state = {
        name: null,
        lastModified: null,
        lastModifier: null,
        loaded: false,
        redirect: false,
        originalName: null,
        reports: [],
        originalReports: [],
        deleteModalVisible: false,
        addButtonVisible: true,
        autoRefreshInterval: null,
        fullScreenActive: false,
        serverError: null,
        isAuthorizedToShare: false
      };
    }

    componentDidMount = async () => {
      await this.renderDashboard();
    };

    renderDashboard = async () => {
      await this.props.mightFail(
        loadDashboard(this.id),
        async response => {
          const isAuthorizedToShare = await isAuthorizedToShareDashboard(this.id);
          const {name, lastModifier, lastModified, reports} = response;

          this.setState({
            lastModifier,
            lastModified,
            loaded: true,
            name,
            originalName: name,
            reports: reports || [],
            originalReports: reports || [],
            isAuthorizedToShare
          });
        },
        error => {
          const serverError = error.status;
          this.setState({
            serverError
          });
          return;
        }
      );
    };

    deleteDashboard = async evt => {
      await remove(this.id);

      this.setState({
        redirect: true
      });
    };

    updateName = evt => {
      this.setState({
        name: evt.target.value
      });
    };

    saveChanges = async () => {
      await update(this.id, {
        name: this.state.name,
        reports: this.state.reports
      });

      this.setState({
        originalName: this.state.name,
        originalReports: this.state.reports,
        isAuthorizedToShare: await isAuthorizedToShareDashboard(this.id)
      });
    };

    cancelChanges = async () => {
      this.setState({
        name: this.state.originalName,
        reports: this.state.originalReports
      });
    };

    addReport = newReport => {
      this.setState({
        reports: [...this.state.reports, newReport]
      });
    };

    deleteReport = ({report: reportToRemove}) => {
      this.setState({
        reports: this.state.reports.filter(report => report !== reportToRemove)
      });
    };

    updateReport = ({report, ...changes}) => {
      const reportIdx = this.state.reports.indexOf(report);

      Object.keys(changes).forEach(prop => {
        changes[prop] = {$set: changes[prop]};
      });

      this.setState({
        reports: updateState(this.state.reports, {
          [reportIdx]: changes
        })
      });
    };

    showDeleteModal = () => {
      this.setState({
        deleteModalVisible: true
      });
    };
    closeDeleteModal = () => {
      this.setState({
        deleteModalVisible: false
      });
    };

    showAddButton = () => {
      this.setState({
        addButtonVisible: true
      });
    };

    hideAddButton = () => {
      this.setState({
        addButtonVisible: false
      });
    };

    toggleFullscreen = () => {
      this.setState(prevState => {
        return {
          fullScreenActive: !prevState.fullScreenActive
        };
      });
    };

    setAutorefresh = timeout => () => {
      this.setState({
        autoRefreshInterval: timeout
      });
    };

    autoRefreshOption = (interval, label) => {
      return (
        <Dropdown.Option
          active={this.state.autoRefreshInterval === interval}
          onClick={this.setAutorefresh(interval)}
          className="Dashboard__autoRefreshOption"
        >
          {label}
        </Dropdown.Option>
      );
    };

    renderEditMode = state => {
      const {name, lastModifier, lastModified} = state;

      return (
        <div className="Dashboard">
          <div className="Dashboard__header">
            <div className="Dashboard__name-container">
              <Input
                type="text"
                id={'name'}
                ref={this.inputRef}
                onChange={this.updateName}
                value={name || ''}
                className="Dashboard__name-input"
                placeholder="Dashboard Name"
                isInvalid={!this.state.name}
              />
              {!this.state.name && (
                <ErrorMessage className="Report__warning">
                  Dashboard's name can not be empty
                </ErrorMessage>
              )}
              <div className="Dashboard__metadata">
                Last modified {moment(lastModified).format('lll')} by {lastModifier}
              </div>
            </div>
            <div className="Dashboard__tools">
              <Link
                className="Button Dashboard__tool-button Dashboard__save-button"
                to={`/dashboard/${this.id}`}
                onClick={this.saveChanges.bind(this)}
                disabled={!this.state.name}
              >
                <Icon type="check" />
                Save
              </Link>
              <Link
                className="Button Dashboard__tool-button Dashboard__cancel-button"
                to={`/dashboard/${this.id}`}
                onClick={this.cancelChanges}
              >
                <Icon type="stop" />
                Cancel
              </Link>
            </div>
          </div>
          <DashboardView
            disableReportScrolling
            loadReport={loadReport}
            reports={this.state.reports}
            reportAddons={[
              <DragBehavior
                key="DragBehavior"
                reports={this.state.reports}
                updateReport={this.updateReport}
                onDragStart={this.hideAddButton}
                onDragEnd={this.showAddButton}
              />,
              <DeleteButton key="DeleteButton" deleteReport={this.deleteReport} />,
              <ResizeHandle
                key="ResizeHandle"
                reports={this.state.reports}
                updateReport={this.updateReport}
                onResizeStart={this.hideAddButton}
                onResizeEnd={this.showAddButton}
              />
            ]}
          >
            <Grid reports={this.state.reports} />
            <DimensionSetter emptyRows={9} reports={this.state.reports} />
            <AddButton addReport={this.addReport} visible={this.state.addButtonVisible} />
          </DashboardView>
        </div>
      );
    };

    renderViewMode = state => {
      const {name, lastModifier, lastModified, deleteModalVisible, isAuthorizedToShare} = state;
      return (
        <Fullscreen
          enabled={this.state.fullScreenActive}
          onChange={fullScreenActive => this.setState({fullScreenActive})}
        >
          <div
            className={classnames('Dashboard', {
              'Dashboard--fullscreen': this.state.fullScreenActive
            })}
          >
            <div className="Dashboard__header">
              <div className="Dashboard__name-container">
                <h1 className="Dashboard__heading">{name}</h1>
                <div className="Dashboard__metadata">
                  Last modified {moment(lastModified).format('lll')} by {lastModifier}
                </div>
              </div>
              <div className="Dashboard__tools">
                {!this.state.fullScreenActive && (
                  <React.Fragment>
                    <Link
                      className="Dashboard__tool-button Dashboard__edit-button"
                      to={`/dashboard/${this.id}/edit`}
                      onClick={this.setAutorefresh(null)}
                    >
                      <Button>
                        <Icon type="edit" />
                        Edit
                      </Button>
                    </Link>
                    <Button
                      onClick={this.showDeleteModal}
                      className="Dashboard__tool-button Dashboard__delete-button"
                    >
                      <Icon type="delete" />
                      Delete
                    </Button>
                    <Popover
                      className="Dashboard__tool-button Dashboard__share-button"
                      icon="share"
                      title="Share"
                      disabled={!isAuthorizedToShare}
                      tooltip={this.createShareTooltip()}
                    >
                      <ShareEntity
                        type="dashboard"
                        resourceId={this.id}
                        shareEntity={shareDashboard}
                        revokeEntitySharing={revokeDashboardSharing}
                        getSharedEntity={getSharedDashboard}
                      />
                    </Popover>
                  </React.Fragment>
                )}
                <Button
                  onClick={this.toggleFullscreen}
                  className="Dashboard__tool-button Dashboard__fullscreen-button"
                >
                  <Icon type={this.state.fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />
                  {this.state.fullScreenActive ? ' Leave' : ' Enter'} Fullscreen
                </Button>
                <Dropdown
                  label={
                    <React.Fragment>
                      <AutoRefreshIcon interval={this.state.autoRefreshInterval} /> Auto Refresh
                    </React.Fragment>
                  }
                  active={!!this.state.autoRefreshInterval}
                >
                  {this.autoRefreshOption(null, 'Off')}
                  {this.autoRefreshOption(1 * 60 * 1000, '1 Minute')}
                  {this.autoRefreshOption(5 * 60 * 1000, '5 Minutes')}
                  {this.autoRefreshOption(10 * 60 * 1000, '10 Minutes')}
                  {this.autoRefreshOption(15 * 60 * 1000, '15 Minutes')}
                  {this.autoRefreshOption(30 * 60 * 1000, '30 Minutes')}
                  {this.autoRefreshOption(60 * 60 * 1000, '60 Minutes')}
                </Dropdown>
              </div>
            </div>
            <Modal
              open={deleteModalVisible}
              onClose={this.closeDeleteModal}
              className="Dashboard__delete-modal"
            >
              <Modal.Header>Delete {this.state.name}</Modal.Header>
              <Modal.Content>
                <p>You are about to delete {this.state.name}. Are you sure you want to proceed?</p>
              </Modal.Content>
              <Modal.Actions>
                <Button
                  className="Dashboard__close-delete-modal-button"
                  onClick={this.closeDeleteModal}
                >
                  Cancel
                </Button>
                <Button
                  type="primary"
                  color="red"
                  className="Dashboard__delete-dashboard-modal-button"
                  onClick={this.deleteDashboard}
                >
                  Delete
                </Button>
              </Modal.Actions>
            </Modal>
            <DashboardView
              loadReport={loadReport}
              reports={this.state.reports}
              reportAddons={
                this.state.autoRefreshInterval && [
                  <AutoRefreshBehavior
                    key="autorefresh"
                    interval={this.state.autoRefreshInterval}
                    renderDashboard={this.renderDashboard}
                  />
                ]
              }
            >
              <DimensionSetter reports={this.state.reports} />
            </DashboardView>
          </div>
        </Fullscreen>
      );
    };

    createShareTooltip = () => {
      return this.state.isAuthorizedToShare
        ? ''
        : 'You are not authorized to share the dashboard, ' +
            " because you don't have access to all reports on the dashboard!";
    };

    inputRef = input => {
      this.nameInput = input;
    };

    componentDidUpdate() {
      if (this.nameInput && this.isNew) {
        this.nameInput.focus();
        this.nameInput.select();
        this.isNew = false;
      }
    }

    render() {
      const {viewMode} = this.props.match.params;

      const {loaded, redirect, serverError} = this.state;

      if (serverError) {
        return <ErrorPage entity="dashboard" statusCode={serverError} />;
      }

      if (!loaded) {
        return <div className="dashboard-loading-indicator">loading...</div>;
      }

      if (redirect) {
        return <Redirect to="/dashboards" />;
      }

      return (
        <div className="Dashboard-container">
          {viewMode === 'edit' ? this.renderEditMode(this.state) : this.renderViewMode(this.state)}
        </div>
      );
    }
  }
);
