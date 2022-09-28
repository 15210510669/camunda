/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Mixpanel} from 'mixpanel-browser';
import {getStage} from './getStage';

const EVENT_PREFIX = 'operate:';
type Events =
  | {
      eventName: 'navigation';
      link:
        | 'header-logo'
        | 'header-dashboard'
        | 'header-processes'
        | 'header-decisions'
        | 'dashboard-running-processes'
        | 'dashboard-processes-with-incidents'
        | 'dashboard-active-processes'
        | 'dashboard-process-instances-by-name-all-versions'
        | 'dashboard-process-instances-by-name-single-version'
        | 'dashboard-process-incidents-by-error-message-all-processes'
        | 'dashboard-process-incidents-by-error-message-single-version'
        | 'processes-instance-details'
        | 'processes-parent-instance-details'
        | 'process-details-parent-details'
        | 'process-details-breadcrumb'
        | 'process-details-called-instances'
        | 'process-details-version'
        | 'decision-instances-instance-details'
        | 'decision-instances-parent-process-details'
        | 'decision-details-parent-process-details'
        | 'decision-details-version';
      currentPage?:
        | 'dashboard'
        | 'processes'
        | 'decisions'
        | 'process-details'
        | 'decision-details'
        | 'login';
    }
  | {
      eventName: 'theme-toggle';
      toggledTo: 'light' | 'dark';
    }
  | {
      eventName: 'batch-operation';
      operationType: OperationEntityType;
    }
  | {
      eventName: 'single-operation';
      operationType: OperationEntityType;
    }
  | {
      eventName: 'instances-loaded';
      filters: string[];
      sortBy?: string;
      sortOrder?: 'desc' | 'asc';
    }
  | {
      eventName: 'decisions-loaded';
      filters: string[];
      sortBy?: string;
      sortOrder?: 'desc' | 'asc';
    }
  | {
      eventName: 'variables-panel-used';
      toTab: string;
    }
  | {
      eventName: 'drd-panel-interaction';
      action: 'open' | 'close' | 'maximize' | 'minimize';
    }
  | {
      eventName: 'operate-loaded';
      theme: 'dark' | 'light';
    }
  | {
      eventName: 'process-instance-details-loaded';
      state: InstanceEntityState;
    }
  | {
      eventName: 'decision-instance-details-loaded';
      state: DecisionInstanceEntityState;
    }
  | {
      eventName: 'incidents-panel-opened';
    }
  | {
      eventName: 'incidents-panel-closed';
    }
  | {
      eventName: 'incidents-sorted';
      column: string;
    }
  | {
      eventName: 'incidents-panel-full-error-message-opened';
    }
  | {
      eventName: 'incident-filtered';
    }
  | {
      eventName: 'incident-filters-cleared';
    }
  | {
      eventName: 'metadata-popover-opened';
    }
  | {
      eventName: 'metadata-popover-closed';
    }
  | {
      eventName: 'diagram-zoom-in';
    }
  | {
      eventName: 'diagram-zoom-out';
    }
  | {
      eventName: 'diagram-zoom-reset';
    }
  | {
      eventName: 'flow-node-instance-details-opened';
    }
  | {
      eventName: 'flow-node-incident-details-opened';
    }
  | {
      eventName: 'json-editor-opened';
      variant: 'add-variable' | 'edit-variable' | 'search-variable';
    }
  | {
      eventName: 'json-editor-closed';
      variant: 'add-variable' | 'edit-variable' | 'search-variable';
    }
  | {
      eventName: 'json-editor-saved';
      variant: 'add-variable' | 'edit-variable' | 'search-variable';
    }
  | {
      eventName: 'instance-history-end-time-toggled';
    }
  | {
      eventName: 'instance-history-item-clicked';
    }
  | {
      eventName: 'enable-modification-mode';
    }
  | {
      eventName: 'apply-modifications-summary';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'apply-modifications';
      isProcessCanceled: boolean;
      addToken: number;
      cancelToken: number;
      moveToken: number;
      addVariable: number;
      editVariable: number;
    }
  | {
      eventName: 'discard-all-summary';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'discard-modifications';
      hasPendingModifications: boolean;
    }
  | {
      eventName: 'undo-modification';
      modificationType:
        | 'ADD_TOKEN'
        | 'CANCEL_TOKEN'
        | 'MOVE_TOKEN'
        | 'ADD_VARIABLE'
        | 'EDIT_VARIABLE';
    }
  | {
      eventName: 'add-token';
    }
  | {
      eventName: 'cancel-token';
    }
  | {
      eventName: 'move-token';
    }
  | {
      eventName: 'modification-successful';
    }
  | {
      eventName: 'modification-failed';
    }
  | {
      eventName: 'leave-modification-mode';
    };

const STAGE_ENV = getStage(window.location.host);

function injectScript(src: string): Promise<void> {
  return new Promise((resolve) => {
    const scriptElement = document.createElement('script');

    scriptElement.src = src;

    document.head.appendChild(scriptElement);

    setTimeout(resolve, 1000);

    scriptElement.onload = () => {
      resolve();
    };
    scriptElement.onerror = () => {
      resolve();
    };
  });
}

class Tracking {
  #mixpanel: null | Mixpanel = null;
  #appCues: null | NonNullable<typeof window.Appcues> = null;
  #baseProperties = {
    organizationId: window.clientConfig?.organizationId,
    clusterId: window.clientConfig?.clusterId,
    stage: STAGE_ENV,
    version: process.env.REACT_APP_VERSION,
  } as const;

  track(events: Events) {
    const {eventName, ...properties} = events;
    const prefixedEventName = `${EVENT_PREFIX}${eventName}`;

    try {
      this.#mixpanel?.track(prefixedEventName, properties);
      this.#appCues?.track(prefixedEventName, {
        ...this.#baseProperties,
        ...properties,
      });
    } catch (error) {
      console.error(`Can't track event: ${eventName}`, error);
    }
  }

  identifyUser = (user: {
    userId: string;
    salesPlanType: string | null;
    roles: ReadonlyArray<string> | null;
  }) => {
    this.#mixpanel?.identify(user.userId);
    this.#appCues?.identify(user.userId, {
      orgId: this.#baseProperties.organizationId,
      salesPlanType: user.salesPlanType ?? '',
      roles: user.roles?.join('|'),
      clusters: this.#baseProperties.clusterId,
    });
  };

  trackPagination = () => {
    this.#appCues?.page();
  };

  #isTrackingAllowed = () => {
    return Boolean(window.Osano?.cm?.analytics);
  };

  #loadMixpanel = (): Promise<void> => {
    if (!this.#isTrackingAllowed()) {
      return Promise.resolve();
    }

    return import('mixpanel-browser').then(({default: mixpanel}) => {
      mixpanel.init(
        window.clientConfig?.mixpanelToken ??
          process.env.REACT_APP_MIXPANEL_TOKEN,
        {
          api_host:
            window.clientConfig?.mixpanelAPIHost ??
            process.env.REACT_MIXPANEL_HOST,
        }
      );
      this.#mixpanel?.register(this.#baseProperties);
      this.#mixpanel = mixpanel;
    });
  };

  #loadOsano = (): Promise<void> => {
    return new Promise((resolve) => {
      if (STAGE_ENV === 'int') {
        return injectScript(process.env.REACT_APP_OSANO_INT_ENV_URL).then(
          resolve
        );
      }

      if (STAGE_ENV === 'prod') {
        return injectScript(process.env.REACT_APP_OSANO_PROD_ENV_URL).then(
          resolve
        );
      }

      return resolve();
    });
  };

  #loadAppCues = (): Promise<void> => {
    if (!this.#isTrackingAllowed()) {
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      return injectScript(process.env.REACT_APP_CUES_HOST).then(() => {
        if (window.Appcues) {
          this.#appCues = window.Appcues;
        }

        resolve();
      });
    });
  };

  loadAnalyticsToWillingUsers(): Promise<void[] | void> {
    if (
      process.env.NODE_ENV === 'development' ||
      !['prod', 'int'].includes(STAGE_ENV) ||
      !window.clientConfig?.organizationId
    ) {
      return Promise.resolve();
    }

    return this.#loadOsano().then(() =>
      Promise.all([this.#loadMixpanel(), this.#loadAppCues()])
    );
  }
}

const tracking = new Tracking();

export {tracking};
