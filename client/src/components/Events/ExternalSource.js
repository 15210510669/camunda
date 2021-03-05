/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useMemo} from 'react';
import debounce from 'debounce';
import classnames from 'classnames';

import {Button, Checklist, DocsLink, LabeledInput} from 'components';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {loadExternalGroups} from './service';

import './ExternalSource.scss';

const debounceRequest = debouncePromise();
const externalSource = {type: 'external', configuration: {includeAllGroups: true, group: null}};
const pageSize = 10;

export function ExternalSource({empty, mightFail, onChange, existingSources}) {
  const [availableValues, setAvailableValues] = useState([]);
  const [valuesToLoad, setValuesToLoad] = useState(pageSize);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [query, setQuery] = useState('');
  const [hasMore, setHasMore] = useState(false);

  const search = useMemo(() => debounce((query) => setSearchTerm(query), 500), []);

  useEffect(() => {
    setLoading(true);
    setValuesToLoad(pageSize);
    search(query);
  }, [query, search]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await debounceRequest(
        mightFail,
        0,
        loadExternalGroups({searchTerm, limit: valuesToLoad + 1}),
        (groups) => {
          setAvailableValues(groups.slice(0, valuesToLoad));
          setHasMore(groups.length > valuesToLoad);
        },
        showError
      );
      setLoading(false);
    })();
  }, [mightFail, searchTerm, valuesToLoad]);

  if (empty) {
    return (
      <div className="ExternalSource empty">
        {t('events.table.seeDocs')}
        <DocsLink location="technical-guide/rest-api/event-ingestion/">
          {t('events.table.documentation')}
        </DocsLink>
        .
      </div>
    );
  }

  const toggleAllEventsGroup = ({target: {checked}}) => onChange(checked ? [externalSource] : []);

  const selectAll = existingSources.some((src) => src.configuration.includeAllGroups);
  const selected = existingSources.map((src) => src.configuration.group);

  return (
    <div className="ExternalSource">
      <span className="title">{t('events.sources.eventGroups')}</span>
      <Checklist
        preItems={
          !loading &&
          !query && (
            <LabeledInput
              className={classnames({highlight: selectAll})}
              checked={selectAll}
              type="checkbox"
              label={t('events.sources.allInOne')}
              onChange={toggleAllEventsGroup}
            />
          )
        }
        selectedItems={selected}
        allItems={availableValues}
        onSearch={setQuery}
        onChange={(selected) =>
          onChange(
            selected.map((group) => ({
              type: 'external',
              configuration: {includeAllGroups: false, group},
            }))
          )
        }
        loading={loading}
        formatter={(values, selectedValues) =>
          values.map((value) => ({
            id: value,
            label: formatGroup(value),
            checked: selectAll || selectedValues.includes(value),
            disabled: selectAll,
          }))
        }
        labels={{
          search: t('events.sources.search'),
          empty: t('events.sources.noGroups'),
        }}
      />
      {!loading && hasMore && (
        <Button className="loadMore" onClick={() => setValuesToLoad(valuesToLoad + pageSize)} link>
          {t('common.filter.variableModal.loadMore')}
        </Button>
      )}
    </div>
  );
}

export default withErrorHandling(ExternalSource);

function formatGroup(val) {
  return val === null ? t('events.sources.ungrouped') : val;
}
