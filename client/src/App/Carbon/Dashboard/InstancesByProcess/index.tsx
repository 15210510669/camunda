/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {observer} from 'mobx-react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {getAccordionLabel} from './utils/getAccordionLabel';
import {InstancesBar} from 'modules/components/Carbon/InstancesBar';
import {LinkWrapper, ErrorMessage} from '../styled';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {EmptyState} from 'modules/components/Carbon/EmptyState';
import {ReactComponent as EmptyStateProcessInstancesByName} from 'modules/components/Icon/empty-state-process-instances-by-name.svg';
import {authenticationStore} from 'modules/stores/authentication';
import {Details} from './Details';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;

  const modelerLink = authenticationStore.state.c8Links.modeler;

  if (['initial', 'first-fetch'].includes(status)) {
    return <Skeleton />;
  }

  if (hasNoInstances) {
    return (
      <EmptyState
        icon={
          <EmptyStateProcessInstancesByName title="Start by deploying a process" />
        }
        heading="Start by deploying a process"
        description="There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress."
        link={{
          label: 'Learn more about Operate',
          href: 'https://docs.camunda.io/docs/components/operate/operate-introduction/',
          onClick: () =>
            tracking.track({
              eventName: 'dashboard-link-clicked',
              link: 'operate-docs',
            }),
        }}
        button={
          modelerLink !== undefined
            ? {
                label: 'Go to Modeler',
                href: modelerLink,
                onClick: () =>
                  tracking.track({
                    eventName: 'dashboard-link-clicked',
                    link: 'modeler',
                  }),
              }
            : undefined
        }
      />
    );
  }

  if (status === 'error') {
    return <ErrorMessage />;
  }

  return (
    <PartiallyExpandableDataTable
      dataTestId="instances-by-process"
      headers={[{key: 'instance', header: 'instance'}]}
      rows={processInstances.map((item, index) => {
        const {
          instancesWithActiveIncidentsCount,
          activeInstancesCount,
          processName,
          bpmnProcessId,
          processes,
        } = item;
        const name = processName || bpmnProcessId;
        const version = processes.length === 1 ? processes[0]!.version : 'all';
        const totalInstancesCount =
          instancesWithActiveIncidentsCount + activeInstancesCount;

        return {
          id: bpmnProcessId,
          instance: (
            <LinkWrapper
              to={Locations.processes({
                process: bpmnProcessId,
                version: version.toString(),
                active: true,
                incidents: true,
                ...(totalInstancesCount === 0
                  ? {
                      completed: true,
                      canceled: true,
                    }
                  : {}),
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-instances-by-name-all-versions',
                });
              }}
              title={getAccordionTitle(
                name,
                totalInstancesCount,
                processes.length,
              )}
            >
              <InstancesBar
                label={{
                  type: 'process',
                  size: 'medium',
                  text: getAccordionLabel(
                    name,
                    totalInstancesCount,
                    processes.length,
                  ),
                }}
                incidentsCount={instancesWithActiveIncidentsCount}
                activeInstancesCount={activeInstancesCount}
                size="medium"
              />
            </LinkWrapper>
          ),
        };
      })}
      expandedContents={processInstances.reduce(
        (accumulator, {bpmnProcessId, processName, processes}) => {
          if (processes.length <= 1) {
            return accumulator;
          }

          return {
            ...accumulator,
            [bpmnProcessId]: (
              <Details
                processName={processName || bpmnProcessId}
                processes={processes}
              />
            ),
          };
        },
        {},
      )}
    />
  );
});
export {InstancesByProcess};
