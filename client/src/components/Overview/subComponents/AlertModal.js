import React from 'react';
import update from 'immutability-helper';

import {
  Modal,
  Button,
  Labeled,
  Input,
  LabeledInput,
  Select,
  ErrorMessage,
  Typeahead
} from 'components';
import {emailNotificationIsEnabled} from './service';

import ThresholdInput from './ThresholdInput';

import './AlertModal.scss';

import {formatters} from 'services';

const newAlert = {
  name: 'New Alert',
  email: '',
  reportId: '',
  thresholdOperator: '>',
  threshold: '100',
  checkInterval: {
    value: '10',
    unit: 'minutes'
  },
  reminder: null,
  fixNotification: false
};

export default function AlertModal(reports) {
  return class AlertModal extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        ...newAlert,
        errorInput: 'email'
      };
    }

    componentDidMount = async () => {
      const alert = this.props.entity;
      if (alert && Object.keys(alert).length) this.updateAlert();
      this.setState({
        emailNotificationIsEnabled: await emailNotificationIsEnabled()
      });
    };

    updateAlert() {
      const alert = this.props.entity;

      this.setState(
        (alert &&
          alert.id && {
            ...alert,
            threshold:
              this.getReportType(alert.reportId) === 'duration'
                ? formatters.convertDurationToObject(alert.threshold)
                : alert.threshold.toString(),
            checkInterval: {
              value: alert.checkInterval.value.toString(),
              unit: alert.checkInterval.unit
            },
            reminder: alert.reminder
              ? {
                  value: alert.reminder.value.toString(),
                  unit: alert.reminder.unit
                }
              : null
          }) ||
          newAlert
      );
    }

    updateReminder = ({target: {checked}}) => {
      if (checked) {
        this.setState({
          reminder: {
            value: '2',
            unit: 'hours'
          }
        });
      } else {
        this.setState({
          reminder: null
        });
      }
    };

    setErrorField = field => {
      if (this.state.errorInput !== field) {
        this.setState({
          errorInput: field
        });
      }
    };

    confirm = () => {
      this.props.onConfirm({
        ...this.state,
        threshold: formatters.convertDurationToSingleNumber(this.state.threshold)
      });
    };

    isInEditingMode = () => {
      return this.props.entity && this.props.entity.id;
    };

    isThresholdValid = () => {
      const value = this.getThresholdValue();
      return value.trim() && !isNaN(value);
    };

    componentDidUpdate({entity}) {
      if (this.props.entity !== entity) {
        this.updateAlert();
      }
      if (!this.state.name.trim()) {
        this.setErrorField('name');
        return;
      }
      if (
        !this.state.email.match(
          /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/
        )
      ) {
        // taken from https://www.w3.org/TR/2012/WD-html-markup-20120320/input.email.html#input.email.attrs.value.single
        this.setErrorField('email');
        return;
      }
      if (!this.state.reportId) {
        this.setErrorField('report');
        return;
      }
      if (!this.isThresholdValid()) {
        this.setErrorField('threshold');
        return;
      }
      if (
        !this.state.checkInterval.value.trim() ||
        isNaN(this.state.checkInterval.value.trim()) ||
        !(this.state.checkInterval.value > 0)
      ) {
        this.setErrorField('checkInterval');
        return;
      }
      if (
        this.state.reminder !== null &&
        (!this.state.reminder.value.trim() ||
          isNaN(this.state.reminder.value.trim()) ||
          !this.state.reminder.value > 0)
      ) {
        this.setErrorField('reminder');
        return;
      }
      this.setErrorField(null);
    }

    getReportType = reportId => {
      const report = reports.find(({id}) => id === reportId);

      return report && report.data.view.property;
    };

    getThresholdValue = () =>
      typeof this.state.threshold.value !== 'undefined'
        ? this.state.threshold.value
        : this.state.threshold;

    updateReport = ({id}) => {
      const reportType = this.getReportType(id);
      const currentValue = this.getThresholdValue();

      this.setState({
        reportId: id,
        threshold: reportType === 'duration' ? {value: currentValue, unit: 'days'} : currentValue
      });
    };

    render() {
      const {
        name,
        email,
        reportId,
        thresholdOperator,
        threshold,
        checkInterval,
        reminder,
        fixNotification,
        emailNotificationIsEnabled,
        errorInput
      } = this.state;
      return (
        <Modal open={this.props.entity} onClose={this.props.onClose}>
          <Modal.Header>{this.isInEditingMode() ? 'Edit Alert' : 'Add new Alert'}</Modal.Header>
          <Modal.Content>
            <div className="AlertModal__topSection">
              <div className="AlertModal__inputGroup">
                {!emailNotificationIsEnabled && (
                  <span className="AlertModal__configuration-warning">
                    Email notification service is not configured. Please check the{' '}
                    {
                      <a href="https://docs.camunda.org/optimize/latest/technical-guide/configuration/#alerting">
                        Optimize documentation
                      </a>
                    }
                  </span>
                )}
                <LabeledInput
                  id="name-input"
                  className="AlertModal__input"
                  label="Name"
                  isInvalid={errorInput === 'name'}
                  value={name}
                  onChange={({target: {value}}) => this.setState({name: value})}
                >
                  {errorInput === 'name' && (
                    <ErrorMessage className="AlertModal__warning">Please enter a name</ErrorMessage>
                  )}
                </LabeledInput>
              </div>
              <div className="AlertModal__inputGroup">
                <LabeledInput
                  id="email-input"
                  label="Send Email to"
                  className="AlertModal__input"
                  isInvalid={errorInput === 'email'}
                  value={email}
                  onChange={({target: {value}}) => this.setState({email: value})}
                >
                  {errorInput === 'email' && (
                    <ErrorMessage className="AlertModal__warning">
                      Please enter a valid Email address
                    </ErrorMessage>
                  )}
                </LabeledInput>
              </div>
              <div className="AlertModal__inputGroup">
                <Labeled label="when Report">
                  <Typeahead
                    initialValue={reports.find(report => report.id === reportId)}
                    isInvalid={errorInput === 'report'}
                    placeholder="Please select Report"
                    values={reports}
                    onSelect={this.updateReport}
                    formatter={({name}) => name}
                  />
                </Labeled>
                <div className="AlertModal__report-selection-note">
                  Note: you can only create an alert for a report visualized as Number
                </div>
              </div>
              <div className="AlertModal__inputGroup">
                <div className="AlertModal__combinedInput">
                  <Labeled label="has a value">
                    <Select
                      value={thresholdOperator}
                      className="thresholdSelect"
                      onChange={({target: {value}}) => this.setState({thresholdOperator: value})}
                    >
                      <Select.Option value=">">above</Select.Option>
                      <Select.Option value="<">below</Select.Option>
                    </Select>
                  </Labeled>
                  <ThresholdInput
                    id="value-input"
                    value={threshold}
                    onChange={threshold => this.setState({threshold})}
                    isInvalid={errorInput === 'threshold'}
                    type={this.getReportType(reportId)}
                  />
                </div>
                {errorInput === 'threshold' && (
                  <ErrorMessage className="AlertModal__warning">
                    Please enter a numeric value
                  </ErrorMessage>
                )}
              </div>
            </div>
            <div className="AlertModal__inputGroup">
              <div className="AlertModal__combinedInput">
                <LabeledInput
                  id="checkInterval-input"
                  label="Check Report every"
                  className="AlertModal__input"
                  isInvalid={errorInput === 'checkInterval'}
                  value={checkInterval.value}
                  onChange={({target: {value}}) =>
                    this.setState(update(this.state, {checkInterval: {value: {$set: value}}}))
                  }
                />
                <Select
                  value={checkInterval.unit}
                  onChange={({target: {value}}) =>
                    this.setState(update(this.state, {checkInterval: {unit: {$set: value}}}))
                  }
                >
                  <Select.Option value="seconds">Seconds</Select.Option>
                  <Select.Option value="minutes">Minutes</Select.Option>
                  <Select.Option value="hours">Hours</Select.Option>
                  <Select.Option value="days">Days</Select.Option>
                  <Select.Option value="weeks">Weeks</Select.Option>
                  <Select.Option value="months">Months</Select.Option>
                </Select>
              </div>
              {errorInput === 'checkInterval' && (
                <ErrorMessage className="AlertModal__warning">
                  Please enter a numeric value
                </ErrorMessage>
              )}
            </div>
            <div className="AlertModal__inputGroup">
              <Input
                id="notification-checkbox"
                type="checkbox"
                checked={fixNotification}
                onChange={({target: {checked}}) => this.setState({fixNotification: checked})}
              />
              <label htmlFor="notification-checkbox">Send notification when resolved</label>
            </div>
            <div className="AlertModal__inputGroup">
              <Input
                id="reminder-checkbox"
                type="checkbox"
                checked={!!reminder}
                onChange={this.updateReminder}
              />
              <label htmlFor="reminder-checkbox">Send reminder notification</label>
              {reminder && (
                <div className="AlertModal__inputGroup">
                  <div className="AlertModal__combinedInput">
                    <LabeledInput
                      id="reminder-input"
                      label="every"
                      className="AlertModal__input"
                      isInvalid={errorInput === 'reminder'}
                      value={reminder.value}
                      onChange={({target: {value}}) =>
                        this.setState(update(this.state, {reminder: {value: {$set: value}}}))
                      }
                    />
                    <Select
                      value={reminder.unit}
                      onChange={({target: {value}}) =>
                        this.setState(update(this.state, {reminder: {unit: {$set: value}}}))
                      }
                    >
                      <Select.Option value="minutes">Minutes</Select.Option>
                      <Select.Option value="hours">Hours</Select.Option>
                      <Select.Option value="days">Days</Select.Option>
                      <Select.Option value="weeks">Weeks</Select.Option>
                      <Select.Option value="months">Months</Select.Option>
                    </Select>
                  </div>
                  {errorInput === 'reminder' && (
                    <ErrorMessage className="AlertModal__warning">
                      Please enter a numeric value
                    </ErrorMessage>
                  )}
                </div>
              )}
            </div>
          </Modal.Content>
          <Modal.Actions>
            <Button onClick={this.props.onClose}>Cancel</Button>
            <Button
              type="primary"
              color="blue"
              onClick={this.confirm}
              disabled={this.state.errorInput !== null}
            >
              {this.isInEditingMode() ? 'Apply Changes' : 'Add Alert'}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  };
}
