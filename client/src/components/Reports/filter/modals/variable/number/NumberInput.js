import React from 'react';

import {ButtonGroup, Button, Input, ErrorMessage} from 'components';
import classnames from 'classnames';

import './NumberInput.css';

export default class NumberInput extends React.Component {
  static defaultFilter = {operator: 'in', values: ['']};

  componentDidMount() {
    this.props.setValid(false);
  }

  setOperator = operator => evt => {
    evt.preventDefault();

    let {values} = this.props.filter;
    if (operator === '<' || operator === '>') {
      values = [values[0]];
    }

    this.props.changeFilter({operator, values});
  };

  selectionIsValid = () => {
    const {values} = this.props.filter;

    return (values || []).every(this.isValid);
  };

  isValid = value => value.trim() && !isNaN(value.trim());

  addValue = evt => {
    evt.preventDefault();

    this.props.changeFilter({...this.props.filter, values: [...this.props.filter.values, '']});
  };

  removeValue = index => {
    this.props.changeFilter({
      ...this.props.filter,
      values: this.props.filter.values.filter((_, idx) => idx !== index)
    });
  };

  changeValue = ({target}) => {
    const values = [...this.props.filter.values];
    values[target.getAttribute('data-idx')] = target.value;

    this.props.changeFilter({...this.props.filter, values});
  };

  componentDidUpdate(prevProps) {
    if (prevProps.filter.values !== this.props.filter.values) {
      this.props.setValid(this.selectionIsValid());
    }
  }

  render() {
    const {operator, values} = this.props.filter;
    const onlyOneValueAllowed = operator === '<' || operator === '>';

    return (
      <React.Fragment>
        <div className="VariableFilter__buttonRow">
          <ButtonGroup>
            <Button
              onClick={this.setOperator('in')}
              className={classnames({'is-active': operator === 'in'})}
            >
              is
            </Button>
            <Button
              onClick={this.setOperator('not in')}
              className={classnames({'is-active': operator === 'not in'})}
            >
              is not
            </Button>
            <Button
              onClick={this.setOperator('<')}
              className={classnames({'is-active': operator === '<'})}
            >
              is less than
            </Button>
            <Button
              onClick={this.setOperator('>')}
              className={classnames({'is-active': operator === '>'})}
            >
              is greater than
            </Button>
          </ButtonGroup>
        </div>
        <div className="VariableFilter__valueFields">
          <ul className="NumberInput__valueList NumberInput__valueList--inputs">
            {(values || []).map((value, idx) => {
              return (
                <li key={idx} className="NumberInput__valueListItem">
                  <Input
                    type="text"
                    value={value}
                    data-idx={idx}
                    onChange={this.changeValue}
                    placeholder="Enter value"
                    isInvalid={!this.isValid(value)}
                  />
                  {values.length > 1 && (
                    <Button
                      onClick={evt => {
                        evt.preventDefault();
                        this.removeValue(idx);
                      }}
                      className="NumberInput__removeItemButton"
                    >
                      ×
                    </Button>
                  )}
                </li>
              );
            })}
            {!this.selectionIsValid() && (
              <li className="NumberInput__valueListWarning">
                <ErrorMessage>All fields should have a numeric value</ErrorMessage>
              </li>
            )}
            {!onlyOneValueAllowed && (
              <li className="NumberInput__valueListButton">
                <Button onClick={this.addValue} className="NumberInput__addValueButton">
                  Add Value
                </Button>
              </li>
            )}
          </ul>
        </div>
      </React.Fragment>
    );
  }
}
