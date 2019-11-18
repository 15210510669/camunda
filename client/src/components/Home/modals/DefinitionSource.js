/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ItemsList from './ItemsList';
import {Typeahead, LoadingIndicator, Form, Labeled} from 'components';
import {t} from 'translation';
import equal from 'deep-equal';
import {formatDefintionName, formatTenants} from './service';

export default class DefinitionSource extends React.Component {
  state = {
    selectedDefinition: null,
    selectedTenants: []
  };

  componentDidUpdate(_, prevState) {
    const {selectedDefinition, selectedTenants} = this.state;
    if (
      !equal(prevState.selectedDefinition, selectedDefinition) ||
      !equal(prevState.selectedTenants, selectedTenants)
    ) {
      if (selectedDefinition && selectedTenants.length) {
        this.props.onChange([
          {
            definitionType: selectedDefinition.type,
            definitionKey: selectedDefinition.key,
            tenants: selectedTenants.map(({id}) => id)
          }
        ]);
      } else {
        this.props.setInvalid();
      }
    }
  }

  updateSelectedDefinition = selectedDefinition => {
    if (selectedDefinition.tenants.length === 1) {
      // preselect if there is only one tenant
      this.setState({selectedDefinition, selectedTenants: [selectedDefinition.tenants[0]]});
    } else {
      this.setState({selectedDefinition, selectedTenants: []});
    }
  };

  render() {
    const {selectedDefinition, selectedTenants} = this.state;
    const {definitionsWithTenants} = this.props;

    if (!definitionsWithTenants) {
      return <LoadingIndicator />;
    }

    return (
      <>
        <Form.Group>
          <Labeled label={t('home.sources.definition.label-plural')}>
            <Typeahead
              disabled={definitionsWithTenants.length === 0}
              placeholder={t('common.select')}
              values={definitionsWithTenants}
              onSelect={this.updateSelectedDefinition}
              formatter={formatDefintionName}
              noValuesMessage={t('common.definitionSelection.noDefinition')}
            />
          </Labeled>
        </Form.Group>
        {selectedDefinition && selectedDefinition.tenants.length !== 1 && (
          <Form.Group>
            <Labeled label={t('common.tenant.label-plural')}>
              <ItemsList
                selectedItems={selectedTenants}
                allItems={selectedDefinition.tenants}
                onChange={selectedTenants => this.setState({selectedTenants})}
                formatter={formatTenants}
              />
            </Labeled>
          </Form.Group>
        )}
      </>
    );
  }
}
