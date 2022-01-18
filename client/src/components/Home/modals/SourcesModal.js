/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';

import {
  Button,
  Modal,
  Table,
  Input,
  TenantPopover,
  Typeahead,
  Labeled,
  Tag,
  Icon,
} from 'components';
import {formatters} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {areTenantsAvailable} from 'config';

import {getDefinitionsWithTenants, getTenantsWithDefinitions} from './service';

import './SourcesModal.scss';

const {formatTenantName} = formatters;

export function SourcesModal({onClose, onConfirm, mightFail, confirmText, preSelectAll}) {
  const [definitions, setDefinitions] = useState();
  const [tenants, setTenants] = useState([]);
  const [selected, setSelected] = useState([]);
  const [selectedTenant, setSelectedTenant] = useState();
  const [query, setQuery] = useState('');

  useEffect(() => {
    mightFail(
      getDefinitionsWithTenants(),
      (definitions) => {
        if (preSelectAll) {
          setSelected(definitions.map(format));
        }
        setDefinitions(definitions);
      },
      showError
    );
  }, [mightFail, preSelectAll]);

  useEffect(() => {
    (async () => {
      const tenantAvailable = await areTenantsAvailable();
      if (tenantAvailable) {
        mightFail(getTenantsWithDefinitions(), setTenants, showError);
      }
    })();
  }, [mightFail]);

  const removeExtraTenants = (def) => {
    if (typeof selectedTenant === 'undefined') {
      return def;
    }

    return {...def, tenants: [selectedTenant]};
  };

  function createCollection() {
    onConfirm(selected);
  }

  const filteredDefinitions =
    definitions?.filter(
      (def) =>
        definitionHasSelectedTenant(def, selectedTenant) &&
        def.name.toLowerCase().includes(query.toLowerCase())
    ) || [];

  function deselectAll() {
    setSelected(
      selected.concat(
        filteredDefinitions
          .filter(({key}) => !selected.some(({definitionKey}) => key === definitionKey))
          .map(format)
          .map(removeExtraTenants)
      )
    );
  }

  function selectAll() {
    setSelected(
      selected.filter(
        ({definitionKey}) => !filteredDefinitions.some(({key}) => definitionKey === key)
      )
    );
  }

  const tableHead = [
    {
      label: (
        <Input
          type="checkbox"
          className={classnames({hidden: !filteredDefinitions.length})}
          checked={filteredDefinitions.every(({key}) =>
            selected.some(({definitionKey}) => key === definitionKey)
          )}
          onChange={({target: {checked}}) => (checked ? deselectAll() : selectAll())}
        />
      ),
      id: 'checked',
      sortable: false,
      width: 30,
    },
    {label: t('common.name'), id: 'name', sortable: true},
    {label: t('common.entity.type'), id: 'type', sortable: false, width: 80},
  ];

  if (tenants.length !== 0) {
    tableHead.push({
      label: t('common.tenant.label-plural'),
      id: 'tenants',
      sortable: false,
      width: 80,
    });
  }

  return (
    <Modal
      open
      onClose={onClose}
      onConfirm={createCollection}
      size="large"
      className="SourcesModal"
    >
      <Modal.Header>{t('home.sources.add')}</Modal.Header>
      <Modal.Content>
        <div className="header">
          {tenants.length !== 0 && (
            <Labeled label={t('common.tenant.label')}>
              <Typeahead
                placeholder={t('common.select')}
                onChange={(tenant) => {
                  setSelected([]);
                  setSelectedTenant(tenant);
                }}
                noValuesMessage={t('common.notFound')}
              >
                <Typeahead.Option value={undefined}>
                  {t('common.collection.modal.allTenants')}
                </Typeahead.Option>
                {tenants.map((tenant) => (
                  <Typeahead.Option key={tenant.id} value={tenant.id}>
                    {formatTenantName(tenant)}
                  </Typeahead.Option>
                ))}
              </Typeahead>
            </Labeled>
          )}
          <div className="rightHeader">
            <div className="searchInputContainer">
              <Input
                value={query}
                className="searchInput"
                placeholder={t('home.search.name')}
                type="text"
                onChange={(evt) => {
                  setQuery(evt.target.value);
                }}
                onClear={() => {
                  setQuery('');
                }}
              />
              <Icon className="searchIcon" type="search" size="20" />
            </div>
            {selected.length > 0 && (
              <Tag onRemove={() => setSelected([])}>
                {selected.length} {t('common.selected')}
              </Tag>
            )}
          </div>
        </div>
        <Table
          head={tableHead}
          body={filteredDefinitions.map((def) => {
            const selectedDefinition = selected.find(
              ({definitionKey}) => def.key === definitionKey
            );

            const body = [
              <Input
                type="checkbox"
                checked={!!selectedDefinition}
                onChange={({target: {checked}}) =>
                  checked
                    ? setSelected([...selected, removeExtraTenants(format(def))])
                    : setSelected((selected) =>
                        selected.filter(({definitionKey}) => def.key !== definitionKey)
                      )
                }
              />,
              def.name || def.key,
              def.type,
            ];

            if (tenants.length !== 0) {
              body.push(
                <TenantPopover
                  tenants={def.tenants}
                  selected={selectedDefinition?.tenants || ['']}
                  disabled={!selectedDefinition}
                  onChange={(newTenants) => {
                    setSelected(
                      selected.map((selectedDefinition) => {
                        if (def.key === selectedDefinition.definitionKey) {
                          return {
                            ...selectedDefinition,
                            tenants: newTenants.length === 0 ? [def.tenants[0].id] : newTenants,
                          };
                        }
                        return selectedDefinition;
                      })
                    );
                  }}
                  renderInPortal="sourcesModalTenantPopover"
                />
              );
            }

            return body;
          })}
          disablePagination
          noHighlight
          loading={!definitions}
        />
      </Modal.Content>
      <Modal.Actions>
        <Button main className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary className="confirm" onClick={createCollection}>
          {confirmText}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(SourcesModal);

function format({key, type, tenants}) {
  return {
    definitionKey: key,
    definitionType: type,
    tenants: tenants.map(({id}) => id),
  };
}

function definitionHasSelectedTenant(def, selectedTenant) {
  return def.tenants.some(({id}) =>
    typeof selectedTenant !== 'undefined' ? selectedTenant === id : true
  );
}
