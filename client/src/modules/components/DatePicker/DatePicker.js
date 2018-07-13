import React from 'react';
import moment from 'moment';

import {ButtonGroup} from 'components';

import './DatePicker.css';

import DateFields from './DateFields';
import DateButton from './DateButton';

import {isDateValid} from './service';

const DATE_FORMAT = 'YYYY-MM-DD';

export default class DatePicker extends React.Component {
  constructor(props) {
    super(props);

    const initialDates = this.props.initialDates || {};

    this.state = {
      startDate: initialDates.startDate.format(DATE_FORMAT) || moment().format(DATE_FORMAT),
      endDate: initialDates.endDate.format(DATE_FORMAT) || moment().format(DATE_FORMAT),
      valid: true
    };
  }

  render() {
    return (
      <React.Fragment>
        <div className="DatePicker__inputs">
          <DateFields
            format={DATE_FORMAT}
            onDateChange={this.onDateChange}
            startDate={this.state.startDate}
            endDate={this.state.endDate}
            setValidState={this.setValidState}
          />
        </div>
        <div className="DatePicker__buttons">
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.TODAY,
              DateButton.YESTERDAY,
              DateButton.PAST7,
              DateButton.PAST30
            ])}
          </ButtonGroup>
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.THIS_WEEK,
              DateButton.THIS_MONTH,
              DateButton.THIS_YEAR
            ])}
          </ButtonGroup>
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.LAST_WEEK,
              DateButton.LAST_MONTH,
              DateButton.LAST_YEAR
            ])}
          </ButtonGroup>
        </div>
      </React.Fragment>
    );
  }

  componentDidUpdate(prevProps, prevState) {
    const isAllValid = isDateValid(this.state.startDate) && isDateValid(this.state.endDate);
    if (
      this.props.onDateChange &&
      isAllValid &&
      (this.state.startDate !== prevState.startDate ||
        this.state.endDate !== prevState.endDate ||
        this.state.valid !== prevState.valid)
    ) {
      this.props.onDateChange({
        startDate: moment(this.state.startDate, DATE_FORMAT),
        endDate: moment(this.state.endDate, DATE_FORMAT),
        valid: true
      });
    }
  }

  setValidState = valid => this.setState({valid});

  getDateButtons(labels) {
    return labels.map(label => (
      <DateButton format={DATE_FORMAT} dateLabel={label} key={label} setDates={this.setDates} />
    ));
  }

  setDates = dates => this.setState({...dates});

  onDateChange = (name, date) => {
    const dateObj = moment(date, DATE_FORMAT);

    this.setState(
      {
        [name]: date
      },
      () => {
        const isAllValid = isDateValid(this.state.startDate) && isDateValid(this.state.endDate);
        this.setState({valid: isAllValid});

        this.props.onDateChange({
          startDate: moment(this.state.startDate, DATE_FORMAT),
          endDate: moment(this.state.endDate, DATE_FORMAT),
          valid: isAllValid
        });

        if (
          isAllValid &&
          ((name === 'startDate' && dateObj.isAfter(moment(this.state.endDate, DATE_FORMAT))) ||
            (name === 'endDate' && dateObj.isBefore(moment(this.state.startDate, DATE_FORMAT))))
        ) {
          return this.setState({
            startDate: date,
            endDate: date
          });
        }
      }
    );
  };
}
