/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {parse, isValid} from 'date-fns';
import {processesStore} from 'modules/stores/processes/processes.list';
import {getSearchString} from 'modules/utils/getSearchString';
import {Location} from 'react-router-dom';
import {
  generateDecisionKey,
  groupedDecisionsStore,
} from 'modules/stores/groupedDecisions';
import {getValidVariableValues} from './getValidVariableValues';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {generateProcessKey} from '../generateProcessKey';
import {getProcessInstanceFilters} from './getProcessInstanceFilters';
import {
  ProcessInstanceFilters,
  PROCESS_INSTANCE_FILTER_FIELDS,
  BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
} from './shared';

type DecisionInstanceFilterField =
  | 'tenant'
  | 'name'
  | 'version'
  | 'evaluated'
  | 'failed'
  | 'decisionInstanceIds'
  | 'processInstanceId'
  | 'evaluationDateBefore'
  | 'evaluationDateAfter';

type DecisionInstanceFilters = {
  name?: string;
  version?: string;
  evaluated?: boolean;
  failed?: boolean;
  decisionInstanceIds?: string;
  processInstanceId?: string;
  evaluationDateBefore?: string;
  evaluationDateAfter?: string;
  tenant?: string;
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
  parentInstanceId?: string;
  startDateAfter?: string;
  startDateBefore?: string;
  variable?: {
    name: string;
    values: string[];
  };
  processIds?: string[];
  tenantId?: string;
  retriesLeft?: boolean;
};

type DecisionRequestFilters = {
  evaluated?: boolean;
  failed?: boolean;
  ids?: string[];
  processInstanceId?: string;
  evaluationDateAfter?: string;
  evaluationDateBefore?: string;
  decisionDefinitionIds?: string[];
  tenantId?: string;
};

const DECISION_INSTANCE_FILTER_FIELDS: DecisionInstanceFilterField[] = [
  'name',
  'version',
  'evaluated',
  'failed',
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDateAfter',
  'evaluationDateBefore',
  'tenant',
];

const BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS: DecisionInstanceFilterField[] = [
  'failed',
  'evaluated',
];

function getFilters<Fields extends string, Filters>(
  searchParams: string,
  fields: Fields[],
  booleanFields: string[],
): Filters {
  return Array.from(new URLSearchParams(searchParams)).reduce(
    (accumulator, [param, value]) => {
      if (booleanFields.includes(param)) {
        return {
          ...accumulator,
          [param]: value === 'true',
        };
      }

      if (fields.includes(param as Fields)) {
        return {
          ...accumulator,
          [param]: value,
        };
      }

      return accumulator;
    },
    {},
  ) as Filters;
}

function getDecisionInstanceFilters(
  searchParams: string,
): DecisionInstanceFilters {
  return getFilters<DecisionInstanceFilterField, DecisionInstanceFilters>(
    searchParams,
    DECISION_INSTANCE_FILTER_FIELDS,
    BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS,
  );
}

function deleteSearchParams(location: Location, paramsToDelete: string[]) {
  const params = new URLSearchParams(location.search);

  paramsToDelete.forEach((param) => {
    params.delete(param);
  });

  return {
    ...location,
    search: params.toString(),
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

function parseFilterTime(value: string) {
  const HOUR_MINUTES_PATTERN = /^[0-9]{2}:[0-9]{2}$/;
  const HOUR_MINUTES_SECONDS_PATTERN = /^[0-9]{2}:[0-9]{2}:[0-9]{2}$/;

  if (HOUR_MINUTES_PATTERN.test(value)) {
    const parsedDate = parse(value, 'HH:mm', new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
  }

  if (HOUR_MINUTES_SECONDS_PATTERN.test(value)) {
    const parsedDate = parse(value, 'HH:mm:ss', new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
  }
}

function getProcessIds({
  process,
  processVersion,
  tenant,
}: {
  process: string;
  processVersion: string;
  tenant?: string;
}) {
  if (processVersion === 'all') {
    return (
      processesStore.versionsByProcessAndTenant?.[
        generateProcessKey(process, tenant)
      ]?.map(({id}) => id) ?? []
    );
  }

  return (
    processesStore.versionsByProcessAndTenant?.[
      generateProcessKey(process, tenant)
    ]
      ?.filter(({version}) => version === parseInt(processVersion))
      ?.map(({id}) => id) ?? []
  );
}

function getDecisionIds({
  name,
  decisionVersion,
  tenant,
}: {
  name: string;
  decisionVersion: string;
  tenant?: string;
}) {
  return (
    groupedDecisionsStore.decisionVersionsByKey[
      generateDecisionKey(name, tenant)
    ]
      ?.filter(({version}) =>
        decisionVersion === 'all'
          ? true
          : version === parseInt(decisionVersion),
      )
      .map(({id}) => id) ?? []
  );
}

function getProcessInstancesRequestFilters(): RequestFilters {
  const {variable} = variableFilterStore.state;

  const filters = {
    ...getProcessInstanceFilters(getSearchString()),
    variableName: variable?.name,
    variableValues: variable?.values,
  };

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

        if (key === 'retriesLeft' && value === true) {
          return {...accumulator, retriesLeft: true};
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

        if (key === 'parentInstanceId') {
          return {...accumulator, parentInstanceId: value};
        }

        if (
          key === 'version' &&
          filters.process !== undefined &&
          value !== undefined
        ) {
          const processIds = getProcessIds({
            process: filters.process,
            processVersion: value,
            tenant: filters.tenant,
          });

          if (processIds.length > 0) {
            return {
              ...accumulator,
              processIds,
            };
          }
        }

        if (
          (key === 'variableName' || key === 'variableValues') &&
          filters.variableName !== undefined &&
          filters.variableValues !== undefined
        ) {
          const values =
            getValidVariableValues(filters.variableValues)?.map((value) =>
              JSON.stringify(value),
            ) ?? [];

          return {
            ...accumulator,
            variable: {
              name: filters.variableName,
              values,
            },
          };
        }

        if (
          [
            'startDateAfter',
            'startDateBefore',
            'endDateAfter',
            'endDateBefore',
          ].includes(key) &&
          value !== undefined
        ) {
          return {
            ...accumulator,
            [key]: value,
          };
        }

        if (key === 'tenant' && value !== 'all') {
          return {
            ...accumulator,
            tenantId: value,
          };
        }
      }

      return accumulator;
    },
    {},
  );
}

function getDecisionInstancesRequestFilters() {
  const filters = getDecisionInstanceFilters(getSearchString());

  return Object.entries(filters).reduce<DecisionRequestFilters>(
    (accumulator, [key, value]) => {
      if (value === undefined) {
        return accumulator;
      }

      if (typeof value === 'boolean') {
        if (['evaluated', 'failed'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
          };
        }
        if (key === 'failed') {
          return {
            ...accumulator,
            [key]: value,
          };
        }
      } else {
        if (key === 'decisionInstanceIds') {
          return {
            ...accumulator,
            ids: parseIds(value),
          };
        }
        if (key === 'processInstanceId') {
          return {...accumulator, processInstanceId: value};
        }
        if (
          key === 'version' &&
          filters.name !== undefined &&
          value !== undefined
        ) {
          const decisionDefinitionIds = getDecisionIds({
            name: filters.name,
            decisionVersion: value,
            tenant: filters.tenant,
          });

          if (decisionDefinitionIds.length > 0) {
            return {
              ...accumulator,
              decisionDefinitionIds,
            };
          }
        }

        if (['evaluationDateAfter', 'evaluationDateBefore'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
          };
        }

        if (key === 'tenant' && value !== 'all') {
          return {
            ...accumulator,
            tenantId: value,
          };
        }
      }

      return accumulator;
    },
    {},
  );
}

function updateFiltersSearchString<Filters extends object>(
  currentSearch: string,
  newFilters: Filters,
  possibleFilters: Array<keyof Filters>,
  possibleBooleanFilters: Array<keyof Filters>,
) {
  const oldParams = Object.fromEntries(new URLSearchParams(currentSearch));
  const fieldsToDelete = possibleFilters.filter(
    (field) => newFilters[field] === undefined,
  );
  const newParams = new URLSearchParams(
    Object.entries({
      ...oldParams,
      ...newFilters,
    }).filter(([, value]) => value !== ''),
  );

  fieldsToDelete.forEach((field) => {
    if (newParams.has(field.toString())) {
      newParams.delete(field.toString());
    }
  });

  possibleBooleanFilters.forEach((field) => {
    if (newParams.get(field.toString()) === 'false') {
      newParams.delete(field.toString());
    }
  });

  return newParams.toString();
}

function updateProcessFiltersSearchString(
  currentSearch: string,
  newFilters: ProcessInstanceFilters,
) {
  return updateFiltersSearchString<ProcessInstanceFilters>(
    currentSearch,
    newFilters,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  );
}

function updateDecisionsFiltersSearchString(
  currentSearch: string,
  newFilters: DecisionInstanceFilters,
) {
  return updateFiltersSearchString<DecisionInstanceFilters>(
    currentSearch,
    newFilters,
    DECISION_INSTANCE_FILTER_FIELDS,
    BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS,
  );
}

function getSortParams(search?: string): {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
} | null {
  const params = new URLSearchParams(search ?? getSearchString());
  const sort = params.get('sort');

  const PARAM_PATTERN = /^\w{1,}\+(asc|desc)/;

  if (sort !== null && PARAM_PATTERN.test(sort)) {
    const [sortBy, sortOrder] = sort.split('+');

    return {
      sortBy,
      sortOrder,
    } as {sortBy: string; sortOrder: 'asc' | 'desc'};
  }

  return null;
}

export {
  getProcessInstanceFilters,
  parseIds,
  parseFilterTime,
  getProcessInstancesRequestFilters,
  getDecisionInstancesRequestFilters,
  updateProcessFiltersSearchString,
  updateDecisionsFiltersSearchString,
  deleteSearchParams,
  getSortParams,
  getDecisionInstanceFilters,
};
export type {
  RequestFilters,
  DecisionRequestFilters,
  DecisionInstanceFilters,
  DecisionInstanceFilterField,
};
