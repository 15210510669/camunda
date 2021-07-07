/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql, useQuery} from '@apollo/client';
import {Task, Variable} from 'modules/types';
import {useState} from 'react';

type TaskVariablesQueryVariables = Pick<Task, 'id'>;

interface GetTaskVariables {
  task: Pick<Task, 'id' | 'variables'>;
}

const GET_TASK_VARIABLES = gql`
  query GetTaskVariables($id: String!) {
    task(id: $id) {
      id
      variables {
        id
        name
        previewValue
        isValueTruncated
      }
    }
  }
`;

type FullVariableQueryVariables = Pick<Variable, 'id'>;

interface GetFullVariableValueVariables {
  variable: Pick<Variable, 'id' | 'value'>;
}

const GET_FULL_VARIABLE_VALUE = gql`
  query GetFullVariableValue($id: String!) {
    variable(id: $id) {
      id
      value
    }
  }
`;

const mockGetTaskVariables = (id = '0') => ({
  request: {
    query: GET_TASK_VARIABLES,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: {
        __typename: 'Task',
        id,
        variables: [
          {
            id: '0-myVar',
            name: 'myVar',
            previewValue: '"0001"',
            isValueTruncated: false,
            __typename: 'Variable',
          },
          {
            id: '1-isCool',
            name: 'isCool',
            previewValue: '"yes"',
            isValueTruncated: false,
            __typename: 'Variable',
          },
        ],
      },
    },
  },
});

const mockGetTaskEmptyVariables = (id = '0') => ({
  request: {
    query: GET_TASK_VARIABLES,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: {
        __typename: 'Task',
        id,
        variables: [],
      },
    },
  },
});

const mockGetTaskVariablesTruncatedValues = (id = '0') => ({
  request: {
    query: GET_TASK_VARIABLES,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: {
        __typename: 'Task',
        id,
        variables: [
          {
            id: '0-myVar',
            name: 'myVar',
            previewValue: '"000',
            isValueTruncated: true,
            __typename: 'Variable',
          },
        ],
      },
    },
  },
});

const mockGetFullVariableValue = (id = '0-myVar') => ({
  request: {
    query: GET_FULL_VARIABLE_VALUE,
    variables: {
      id,
    },
  },
  result: {
    data: {
      variable: {
        id: '0-myVar',
        value: '"0001"',
        __typename: 'Variable',
      },
    },
  },
});

function useTaskVariables(id: Task['id']) {
  const {data, loading, client} = useQuery<
    GetTaskVariables,
    TaskVariablesQueryVariables
  >(GET_TASK_VARIABLES, {
    variables: {
      id,
    },
  });
  const [variablesLoadingFullValue, setVariablesLoadingFullValue] = useState<
    Variable['id'][]
  >([]);

  async function queryFullVariable(id: Variable['id']) {
    if (data === undefined) {
      return;
    }

    setVariablesLoadingFullValue((ids) => {
      if (ids.includes(id)) {
        return ids;
      }

      return [...ids, id];
    });
    const {data: fullVariableData} = await client.query<
      GetFullVariableValueVariables,
      FullVariableQueryVariables
    >({
      query: GET_FULL_VARIABLE_VALUE,
      variables: {
        id,
      },
    });

    setVariablesLoadingFullValue((ids) =>
      ids.filter((id) => id !== fullVariableData.variable.id),
    );

    if (fullVariableData === undefined) {
      return;
    }

    client.writeQuery({
      query: GET_TASK_VARIABLES,
      data: {
        task: {
          variables: data.task.variables.map((variable) =>
            variable.id === fullVariableData.variable.id
              ? {
                  ...variable,
                  isValueTruncated: false,
                  previewValue: fullVariableData.variable.value,
                }
              : variable,
          ),
        },
      },
    });
  }

  return {
    variables:
      data?.task?.variables.map(({previewValue, ...variable}) => ({
        ...variable,
        value: previewValue,
      })) ?? [],
    loading: loading || data?.task?.variables === undefined,
    queryFullVariable,
    variablesLoadingFullValue,
  };
}

export {
  useTaskVariables,
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
  mockGetTaskVariablesTruncatedValues,
  mockGetFullVariableValue,
};
