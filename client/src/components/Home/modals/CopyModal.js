/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, LabeledInput, Modal, Form, Message} from 'components';
import {t} from 'translation';
import MoveCopy from './MoveCopy';

import './CopyModal.scss';

export default class CopyModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      name: props.entity.name + ` (${t('common.copyLabel')})`,
      moving: false,
      collection: null,
      gotoNew: true
    };
  }

  onConfirm = () => {
    const {name, moving, collection, gotoNew} = this.state;
    if (name && (!moving || collection)) {
      if (this.isCollection() && this.props.jumpToEntity) {
        this.props.onConfirm(name, gotoNew);
      } else {
        this.props.onConfirm(name, moving && gotoNew, moving && collection.id);
      }
    }
  };

  isCollection = () => this.props.entity.entityType === 'collection';

  render() {
    const {onClose, entity, jumpToEntity} = this.props;
    const {name, moving, collection, gotoNew} = this.state;

    return (
      <Modal className="CopyModal" open onClose={onClose} onConfirm={this.onConfirm}>
        <Modal.Header>{t('common.copyName', {name: entity.name})}</Modal.Header>
        <Modal.Content>
          <Form>
            <Form.Group>
              <LabeledInput
                type="text"
                label={t('home.copy.inputLabel')}
                value={name}
                autoComplete="off"
                onChange={({target: {value}}) => this.setState({name: value})}
              />
            </Form.Group>
            {this.isCollection() && <Message>{t('home.copy.copyCollectionInfo')}</Message>}
            {!this.isCollection() && (
              <MoveCopy
                entity={entity}
                parentCollection={this.props.collection}
                moving={moving}
                setMoving={moving => this.setState({moving})}
                collection={collection}
                setCollection={collection => this.setState({collection})}
              />
            )}
            {jumpToEntity && (this.isCollection() || moving) && (
              <Form.Group>
                <LabeledInput
                  label={t('home.copy.gotoNew')}
                  type="checkbox"
                  checked={gotoNew}
                  onChange={({target: {checked}}) => this.setState({gotoNew: checked})}
                />
              </Form.Group>
            )}
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button className="cancel" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={!name || (moving && !collection)}
            variant="primary"
            color="blue"
            className="confirm"
            onClick={this.onConfirm}
          >
            {t('common.copy')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
