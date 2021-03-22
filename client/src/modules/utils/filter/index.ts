/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {addDays, startOfDay, addMinutes, format, parse} from 'date-fns';
import {workflowsStore} from 'modules/stores/workflows';
import {getSearchString} from 'modules/utils/getSearchString';

type FilterFieldsType =
  | 'workflow'
  | 'version'
  | 'ids'
  | 'errorMessage'
  | 'startDate'
  | 'endDate'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled';

type FiltersType = {
  workflow?: string;
  version?: string;
  ids?: string;
  errorMessage?: string;
  startDate?: string;
  endDate?: string;
  flowNodeId?: string;
  variableName?: string;
  variableValue?: string;
  operationId?: string;
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
};

type RequestFilters = {
  running?: boolean;
  active?: boolean;
  incidents?: boolean;
  finished?: boolean;
  canceled?: boolean;
  completed?: boolean;
  activityId?: string;
  batchOperationId?: string;
  endDateAfter?: string;
  endDateBefore?: string;
  errorMessage?: string;
  ids?: string[];
  startDateAfter?: string;
  startDateBefore?: string;
  variable?: {
    name: string;
    value: string;
  };
  workflowIds?: string[];
};

const FILTER_FIELDS: FilterFieldsType[] = [
  'workflow',
  'version',
  'ids',
  'errorMessage',
  'startDate',
  'endDate',
  'flowNodeId',
  'variableName',
  'variableValue',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
];

const BOOLEAN_FILTER_FIELDS = ['active', 'incidents', 'completed', 'canceled'];

function getFilters(
  searchParams: string,
  fields: FilterFieldsType[] = FILTER_FIELDS
): FiltersType {
  return Array.from(new URLSearchParams(searchParams)).reduce(
    (accumulator, [param, value]) => {
      if (BOOLEAN_FILTER_FIELDS.includes(param as FilterFieldsType)) {
        return {
          ...accumulator,
          [param]: value === 'true',
        };
      }

      if (fields.includes(param as FilterFieldsType)) {
        return {
          ...accumulator,
          [param]: value,
        };
      }

      return accumulator;
    },
    {}
  );
}

type GetRequestDatePairReturn =
  | {
      startDateBefore: string;
      startDateAfter: string;
    }
  | {
      endDateBefore: string;
      endDateAfter: string;
    };

function getRequestDatePair(
  date: Date,
  type: 'startDate' | 'endDate'
): GetRequestDatePairReturn {
  const DATE_REQUEST_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
  const hasTime = date.getHours() + date.getMinutes() + date.getSeconds() !== 0;
  const dateAfter = format(
    hasTime ? date : startOfDay(date),
    DATE_REQUEST_FORMAT
  );
  const dateBefore = format(
    hasTime ? addMinutes(date, 1) : addDays(startOfDay(date), 1),
    DATE_REQUEST_FORMAT
  );

  if (type === 'startDate') {
    return {
      startDateBefore: dateBefore,
      startDateAfter: dateAfter,
    };
  }

  return {
    endDateBefore: dateBefore,
    endDateAfter: dateAfter,
  };
}

function parseIds(value: string) {
  return value
    .trim()
    .replace(/,\s/g, '|')
    .replace(/\s{1,}/g, '|')
    .replace(/,{1,}/g, '|')
    .split('|');
}

function parseFilterDate(value: string) {
  const DATE_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
  const DATE_HOUR_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}$/;
  const DATE_HOUR_MINUTES_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}$/;
  const DATE_TIME_PATTERN_WITH = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}:[0-9]{2}$/;
  const trimmedValue = value.trim();

  if (DATE_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd', new Date());
  }

  if (DATE_HOUR_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk', new Date());
  }

  if (DATE_HOUR_MINUTES_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm', new Date());
  }

  if (DATE_TIME_PATTERN_WITH.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm:ss', new Date());
  }
}

function getWorkflowIds(workflow: string, workflowVersion: string) {
  if (workflowVersion === 'all') {
    return (
      workflowsStore.versionsByWorkflow?.[workflow]?.map(({id}) => id) ?? []
    );
  }

  return (
    workflowsStore.versionsByWorkflow?.[workflow]
      ?.filter(({version}) => version === parseInt(workflowVersion))
      ?.map(({id}) => id) ?? []
  );
}

function getRequestFilters(): RequestFilters {
  const filters = getFilters(getSearchString());

  return Object.entries(filters).reduce<RequestFilters>(
    (accumulator, [key, value]): RequestFilters => {
      if (value === undefined) {
        return accumulator;
      }

      if (typeof value === 'boolean') {
        if (['active', 'incidents'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
            ...(value === true ? {running: true} : {}),
          };
        }

        if (['canceled', 'completed'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
            ...(value === true ? {finished: true} : {}),
          };
        }
      } else {
        if (key === 'errorMessage') {
          return {
            ...accumulator,
            errorMessage: value,
          };
        }

        if (key === 'flowNodeId') {
          return {
            ...accumulator,
            activityId: value,
          };
        }

        if (key === 'operationId') {
          return {
            ...accumulator,
            batchOperationId: value,
          };
        }

        if (key === 'ids') {
          return {
            ...accumulator,
            ids: parseIds(value),
          };
        }

        if (
          key === 'version' &&
          filters.workflow !== undefined &&
          value !== undefined
        ) {
          const workflowIds = getWorkflowIds(filters.workflow, value);

          if (workflowIds.length > 0) {
            return {
              ...accumulator,
              workflowIds,
            };
          }
        }

        if (
          (key === 'variableName' || key === 'variableValue') &&
          filters.variableName !== undefined &&
          filters.variableValue !== undefined
        ) {
          return {
            ...accumulator,
            variable: {
              name: filters.variableName,
              value: filters.variableValue,
            },
          };
        }

        const parsedDate = parseFilterDate(value);
        if (
          (key === 'startDate' || key === 'endDate') &&
          parsedDate !== undefined
        ) {
          return {
            ...accumulator,
            ...getRequestDatePair(parsedDate, key),
          };
        }
      }

      return accumulator;
    },
    {}
  );
}

function updateFiltersSearchString(
  currentSearch: string,
  newFilters: FiltersType
) {
  const oldParams = Object.fromEntries(new URLSearchParams(currentSearch));
  const fieldsToDelete = FILTER_FIELDS.filter(
    (field) => newFilters[field] === undefined
  );
  const newParams = new URLSearchParams(
    Object.entries({
      ...oldParams,
      ...newFilters,
    }) as [string, string][]
  );

  fieldsToDelete.forEach((field) => {
    if (newParams.has(field)) {
      newParams.delete(field);
    }
  });

  BOOLEAN_FILTER_FIELDS.forEach((field) => {
    if (newParams.get(field) === 'false') {
      newParams.delete(field);
    }
  });

  return newParams.toString();
}

function getSorting(
  page: 'instances' | 'instance' = 'instances'
): {sortBy: string; sortOrder: 'asc' | 'desc'} {
  const params = new URLSearchParams(getSearchString());
  const sort = params.get('sort');
  const PARAM_PATTERN = /^\w{1,}\+(asc|desc)/;

  if (sort !== null && PARAM_PATTERN.test(sort)) {
    const [sortBy, sortOrder] = sort.split('+');

    return {
      sortBy,
      sortOrder,
    } as {sortBy: string; sortOrder: 'asc' | 'desc'};
  }

  return {
    sortBy: page === 'instances' ? 'workflowName' : 'errorType',
    sortOrder: 'desc',
  };
}

export {
  getFilters,
  parseIds,
  parseFilterDate,
  getRequestFilters,
  updateFiltersSearchString,
  getSorting,
};
export type {FilterFieldsType, FiltersType, RequestFilters};
