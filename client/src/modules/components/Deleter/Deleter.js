/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {Button, Modal, LoadingIndicator} from 'components';
import {showError} from 'notifications';
import {deleteEntity} from 'services';
import {t} from 'translation';

import './Deleter.scss';

const sectionOrder = ['report', 'combined_report', 'dashboard', 'alert', 'collection'];

export default withErrorHandling(
  class Deleter extends React.Component {
    static defaultProps = {
      deleteEntity: ({entityType, id}) => deleteEntity(entityType, id),
      getName: ({name}) => name,
      onDelete: () => {},
    };

    cancelButton = React.createRef();

    state = {
      conflicts: {},
      loading: false,
    };

    componentDidUpdate(prevProps, prevState) {
      const {entity, checkConflicts} = this.props;
      if (prevProps.entity !== entity && entity) {
        if (checkConflicts) {
          this.setState({loading: true});
          this.props.mightFail(
            checkConflicts(entity),
            async ({conflictedItems}) => {
              if (this.props.onConflict && conflictedItems.length > 0) {
                this.props.onConflict(conflictedItems);
              } else {
                this.setState({
                  conflicts: conflictedItems.reduce((obj, conflict) => {
                    obj[conflict.type] = obj[conflict.type] || [];
                    obj[conflict.type].push(conflict);
                    return obj;
                  }, {}),
                });
              }
              this.setState({loading: false});
            },
            (error) => {
              showError(error);
              this.setState({conflicts: {}, loading: false});
            }
          );
        } else {
          this.setState({conflicts: {}, loading: false});
          this.cancelButton.current.focus();
        }
      }

      if (prevState.loading && !this.state.loading) {
        this.cancelButton.current.focus();
      }
    }

    delete = () => {
      const {entity, onDelete, deleteEntity} = this.props;

      this.setState({loading: true});
      this.props.mightFail(
        deleteEntity(entity),
        (...args) => {
          onDelete(...args);
          this.close();
        },
        (error) => {
          showError(error);
          this.setState({loading: false});
        }
      );
    };

    close = () => {
      this.setState({conflicts: {}, loading: false}, this.props.onClose);
    };

    render() {
      const {entity, getName, type, descriptionText, deleteText, deleteButtonText} = this.props;
      const {conflicts, loading} = this.state;

      if (!entity) {
        return null;
      }

      const translatedType = t(`common.deleter.types.${type}`);

      return (
        <Modal open onClose={this.close} onConfirm={this.delete} className="Deleter">
          <Modal.Header>
            {deleteText || t('common.deleteEntity', {entity: translatedType})}
          </Modal.Header>
          <Modal.Content>
            {loading ? (
              <LoadingIndicator />
            ) : (
              <>
                <p>
                  {descriptionText ||
                    t('common.deleter.permanent', {
                      name: getName(entity),
                      type: translatedType,
                    })}
                </p>
                {Object.keys(conflicts)
                  .sort((a, b) => sectionOrder.indexOf(a) - sectionOrder.indexOf(b))
                  .map((conflictType) => (
                    <div key={conflictType}>
                      {t(`common.deleter.affectedMessage.${type}.${conflictType}`)}
                      <ul>
                        {conflicts[conflictType].map(({id, name}) => (
                          <li key={id}>'{name || id}'</li>
                        ))}
                      </ul>
                    </div>
                  ))}
                <p>
                  <b>{t('common.deleter.noUndo')}</b>
                </p>
              </>
            )}
          </Modal.Content>
          <Modal.Actions>
            <Button
              main
              disabled={loading}
              className="close"
              onClick={this.close}
              ref={this.cancelButton}
            >
              {t('common.cancel')}
            </Button>
            <Button main disabled={loading} warning className="confirm" onClick={this.delete}>
              {deleteButtonText || deleteText || t('common.deleteEntity', {entity: translatedType})}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
