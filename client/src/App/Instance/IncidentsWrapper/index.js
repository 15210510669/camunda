/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useMemo} from 'react';
import {isEqual} from 'lodash';

import PropTypes from 'prop-types';
import {IncidentsBanner} from './IncidentsBanner';
import IncidentsOverlay from './IncidentsOverlay';
import {IncidentsTable} from './IncidentsTable';
import {IncidentsFilter} from './IncidentsFilter';
import usePrevious from 'modules/hooks/usePrevious';
import {incidents as incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const IncidentsWrapper = observer(function IncidentsWrapper(props) {
  const {expandState} = props;
  const {incidents, flowNodes, errorTypes} = incidentsStore;

  const [isOpen, setIsOpen] = useState(false);

  const [selectedFlowNodes, setSelectedFlowNodes] = useState([]);
  const [selectedErrorTypes, setSelectedErrorTypes] = useState([]);
  const [isInTransition, setIsInTransition] = useState(false);

  const prevErrorTypes = usePrevious(errorTypes);
  const prevFlowNodes = usePrevious(flowNodes);
  useEffect(() => {
    incidentsStore.init();

    return () => {
      incidentsStore.reset();
    };
  }, []);

  useEffect(() => {
    if (didFiltersChange(prevErrorTypes, errorTypes)) {
      setSelectedErrorTypes(updateFilters(selectedErrorTypes, errorTypes));
    }
  }, [prevErrorTypes, errorTypes, selectedErrorTypes]);

  useEffect(() => {
    if (didFiltersChange(prevFlowNodes, flowNodes)) {
      setSelectedFlowNodes(updateFilters(selectedFlowNodes, flowNodes));
    }
  }, [prevFlowNodes, flowNodes, selectedFlowNodes]);

  function didFiltersChange(previous, current) {
    return previous && !isEqual([...current.keys()], [...previous.keys()]);
  }

  function updateFilters(previous, current) {
    return previous.reduce(
      (updatedFilters, element) =>
        !!current.get(element) ? [...updatedFilters, element] : updatedFilters,
      []
    );
  }

  function handleToggle() {
    !isInTransition && setIsOpen(!isOpen);
  }

  function handleSelection(selectedFilters, updateFilterState, id) {
    let index = selectedFilters.findIndex((item) => item === id);
    let list = [...selectedFilters];
    if (index === -1) {
      list.push(id);
    } else {
      list.splice(index, 1);
    }
    updateFilterState(list);
  }

  const handleErrorTypeSelect = (errorId) => {
    handleSelection(selectedErrorTypes, setSelectedErrorTypes, errorId);
  };

  const handleFlowNodeSelect = (flowNodeId) => {
    handleSelection(selectedFlowNodes, setSelectedFlowNodes, flowNodeId);
  };

  function clearAll() {
    setSelectedErrorTypes([]);
    setSelectedFlowNodes([]);
  }

  const filteredIncidents = useMemo(() => {
    const hasSelectedFlowNodes = Boolean(selectedFlowNodes.length);
    const hasSelectedErrorTypes = Boolean(selectedErrorTypes.length);

    if (!hasSelectedFlowNodes && !hasSelectedErrorTypes) {
      return incidents;
    }

    const isSelected = (item) => {
      if (hasSelectedErrorTypes && hasSelectedFlowNodes) {
        return (
          selectedFlowNodes.includes(item.flowNodeId) &&
          selectedErrorTypes.includes(item.errorType)
        );
      }
      if (hasSelectedErrorTypes) {
        return selectedErrorTypes.includes(item.errorType);
      }

      if (hasSelectedFlowNodes) {
        return selectedFlowNodes.includes(item.flowNodeId);
      }
    };

    return [...incidents].filter((item) => isSelected(item));
  }, [incidents, selectedErrorTypes, selectedFlowNodes]);

  return (
    incidentsStore.incidentsCount > 0 && (
      <>
        <IncidentsBanner
          onClick={handleToggle}
          isOpen={isOpen}
          expandState={expandState}
        />
        <Styled.Transition
          in={isOpen}
          onEnter={() => setIsInTransition(true)}
          onEntered={() => setIsInTransition(false)}
          onExit={() => setIsInTransition(true)}
          onExited={() => setIsInTransition(false)}
          mountOnEnter
          unmountOnExit
          timeout={400}
        >
          <IncidentsOverlay>
            <IncidentsFilter
              selectedFlowNodes={selectedFlowNodes}
              selectedErrorTypes={selectedErrorTypes}
              onFlowNodeSelect={handleFlowNodeSelect}
              onErrorTypeSelect={handleErrorTypeSelect}
              onClearAll={clearAll}
            />
            <IncidentsTable incidents={filteredIncidents} />
          </IncidentsOverlay>
        </Styled.Transition>
      </>
    )
  );
});

IncidentsWrapper.propTypes = {
  expandState: PropTypes.string.isRequired,
};

export {IncidentsWrapper};
