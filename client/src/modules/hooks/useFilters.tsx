/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {
  updateProcessFiltersSearchString,
  getProcessInstanceFilters,
  ProcessInstanceFilters,
  getDecisionInstanceFilters,
} from 'modules/utils/filter';
import {variableFilterStore} from 'modules/stores/variableFilter';

const useFilters = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const setFiltersToURL = (filters: ProcessInstanceFilters) => {
    const {variableName, variableValues, ...filtersWithoutVariable} = filters;

    navigate({
      search: updateProcessFiltersSearchString(
        location.search,
        filtersWithoutVariable,
      ),
    });
  };

  const areProcessInstanceStatesApplied = () => {
    const filters = getProcessInstanceFilters(location.search);

    return (
      filters.active ||
      filters.incidents ||
      filters.completed ||
      filters.canceled
    );
  };

  const areDecisionInstanceStatesApplied = () => {
    const filters = getDecisionInstanceFilters(location.search);

    return filters.evaluated || filters.failed;
  };

  const setFilters = (filters: ProcessInstanceFilters) => {
    setFiltersToURL(filters);
    if (
      filters.variableName !== undefined &&
      filters.variableValues !== undefined
    ) {
      variableFilterStore.setVariable({
        name: filters.variableName,
        values: filters.variableValues,
      });
    }
  };

  const getFilters = () => {
    return {
      ...getProcessInstanceFilters(location.search),
      ...(variableFilterStore.state.variable !== undefined
        ? {
            variableName: variableFilterStore.state.variable?.name,
            variableValues: variableFilterStore.state.variable?.values,
          }
        : {}),
    };
  };

  return {
    setFilters,
    getFilters,
    areProcessInstanceStatesApplied,
    areDecisionInstanceStatesApplied,
  };
};

export {useFilters};
