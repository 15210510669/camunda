/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useParams} from 'react-router-dom';

/* istanbul ignore file */

const pages = {
  initial: '/',
  login: '/login',
  taskDetails(id: string = ':id') {
    return `/${id}`;
  },
  processes(
    options: {tenantId?: string; matchAllChilren?: boolean} = {
      matchAllChilren: false,
    },
  ) {
    const {tenantId, matchAllChilren = false} = options;
    const baseRoute = matchAllChilren ? 'processes/*' : 'processes';
    if (tenantId !== undefined && window.clientConfig?.isMultiTenancyEnabled) {
      return `${baseRoute}?tenantId=${tenantId}`;
    }

    return baseRoute;
  },
  startProcessFromForm: '/new/:bpmnProcessId',
  interalStartProcessFromForm(bpmnProcessId: string = ':bpmnProcessId') {
    return `/processes/${bpmnProcessId}/start`;
  },
} as const;

function useTaskDetailsParams(): {id: string} {
  const {id} = useParams<'id'>();

  return {id: id ?? ''};
}

function useStartProcessParams(): {bpmnProcessId: string} {
  const {bpmnProcessId} = useParams<'bpmnProcessId'>();

  return {bpmnProcessId: bpmnProcessId ?? ''};
}

export {pages, useTaskDetailsParams, useStartProcessParams};
