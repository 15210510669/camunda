/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Table from 'modules/components/Table';
import Button from 'modules/components/Button';
import {IncidentAction} from 'modules/components/Actions';
import ColumnHeader from '../../../Instances/ListView/List/ColumnHeader';
import Modal from 'modules/components/Modal';
import {formatDate} from 'modules/utils/date';
import {isJobIdPresent} from './../service';

import * as Styled from './styled';
const {THead, TBody, TH, TR, TD} = Table;

export default class IncidentsTable extends React.Component {
  static propTypes = {
    incidents: PropTypes.array.isRequired,
    instanceId: PropTypes.string.isRequired,
    forceSpinner: PropTypes.bool,
    selectedIncidents: PropTypes.array,
    sorting: PropTypes.object.isRequired,
    onIncidentOperation: PropTypes.func.isRequired,
    onIncidentSelection: PropTypes.func.isRequired,
    onSort: PropTypes.func.isRequired
  };

  static defaultProps = {
    forceSpinner: false
  };

  state = {
    isModalVisibile: false
  };

  toggleModal = ({content, title}) => {
    this.setState(prevState => ({
      isModalVisibile: !prevState.isModalVisibile,
      modalContent: content ? content : null,
      modalTitle: title ? title : null
    }));
  };

  renderModal = message => {
    return (
      <Modal onModalClose={this.toggleModal}>
        <Modal.Header>{this.state.modalTitle}</Modal.Header>
        <Modal.Body>
          <Modal.BodyText>{this.state.modalContent}</Modal.BodyText>
        </Modal.Body>
        <Modal.Footer>
          <Modal.PrimaryButton title="Close Modal" onClick={this.toggleModal}>
            Close
          </Modal.PrimaryButton>
        </Modal.Footer>
      </Modal>
    );
  };

  handleMoreButtonClick = (e, incident) => {
    e.stopPropagation();
    this.toggleModal({
      content: incident.errorMessage,
      title: `Flow Node ${incident.flowNodeName} Error`
    });
  };

  render() {
    const {incidents} = this.props;

    return (
      <>
        <Table>
          <THead>
            <TR>
              <Styled.FirstTH>
                <ColumnHeader
                  sortKey="errorType"
                  label="Incident Type"
                  sorting={this.props.sorting}
                  onSort={this.props.onSort}
                />
              </Styled.FirstTH>
              <TH>
                <ColumnHeader
                  sortKey="flowNodeName"
                  label="Flow Node"
                  sorting={this.props.sorting}
                  onSort={this.props.onSort}
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="jobId"
                  label="Job Id"
                  sorting={this.props.sorting}
                  onSort={this.props.onSort}
                  disabled={isJobIdPresent(incidents)}
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="creationTime"
                  label="Creation Time"
                  sorting={this.props.sorting}
                  onSort={this.props.onSort}
                />
              </TH>
              <TH>
                <ColumnHeader label="Error Message" />
              </TH>
              <TH>
                <ColumnHeader label="Actions" />
              </TH>
            </TR>
          </THead>
          <TBody>
            {incidents.map((incident, index) => {
              return (
                <Styled.IncidentTR
                  key={incident.id}
                  isSelected={
                    this.props.selectedIncidents &&
                    this.props.selectedIncidents.includes(
                      incident.flowNodeInstanceId
                    )
                  }
                  onClick={this.props.onIncidentSelection.bind(this, {
                    id: incident.flowNodeInstanceId,
                    activityId: incident.flowNodeId
                  })}
                >
                  <TD>
                    <Styled.FirstCell>
                      <Styled.Index>{index + 1}</Styled.Index>
                      {incident.errorType}
                    </Styled.FirstCell>
                  </TD>
                  <TD>{incident.flowNodeName}</TD>
                  <TD>{incident.jobId || '--'}</TD>
                  <TD>{formatDate(incident.creationTime)}</TD>
                  <TD>
                    <Styled.Flex>
                      <Styled.ErrorMessageCell>
                        {incident.errorMessage}
                      </Styled.ErrorMessageCell>
                      {incident.errorMessage.length >= 58 && (
                        <Button
                          size="small"
                          onClick={e => {
                            this.handleMoreButtonClick(e, incident);
                          }}
                        >
                          More...
                        </Button>
                      )}
                    </Styled.Flex>
                  </TD>
                  <TD>
                    <IncidentAction
                      instanceId={this.props.instanceId}
                      onButtonClick={this.props.onIncidentOperation}
                      incident={incident}
                      showSpinner={
                        this.props.forceSpinner || incident.hasActiveOperation
                      }
                    />
                  </TD>
                </Styled.IncidentTR>
              );
            })}
          </TBody>
        </Table>
        {this.state.isModalVisibile && this.renderModal()}
      </>
    );
  }
}
