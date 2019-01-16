import React from 'react';

import {DurationHeatmapModal} from './DurationHeatmap';

import {ButtonGroup, Button, Icon} from 'components';

import './TargetValueComparison.scss';

export default class TargetValueComparison extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false
    };
  }

  getConfig = () => {
    return this.props.configuration.heatmapTargetValue || {};
  };

  toggleMode = () => {
    const {active, values} = this.getConfig();

    if (active) {
      this.setActive(false);
    } else if (!values || Object.keys(values).length === 0) {
      this.openModal();
    } else {
      this.setActive(true);
    }
  };

  setActive = active => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          active: {$set: active}
        }
      }
    });
  };

  openModal = async () => {
    this.setState({
      modalOpen: true
    });
  };

  closeModal = () => {
    this.setState({
      modalOpen: false
    });
  };

  confirmModal = values => {
    this.props.onChange({
      configuration: {
        heatmapTargetValue: {
          $set: {
            active: Object.keys(values).length > 0,
            values
          }
        }
      }
    });
    this.closeModal();
  };

  render() {
    return (
      <ButtonGroup className="TargetValueComparison">
        <Button
          className="TargetValueComparison__toggleButton"
          active={this.getConfig().active}
          onClick={this.toggleMode}
        >
          Target Value
        </Button>
        <Button className="TargetValueComparison__editButton" onClick={this.openModal}>
          <Icon type="settings" />
        </Button>
        <DurationHeatmapModal
          open={this.state.modalOpen}
          onClose={this.closeModal}
          configuration={this.props.configuration}
          onConfirm={this.confirmModal}
          reportResult={this.props.reportResult}
        />
      </ButtonGroup>
    );
  }
}
