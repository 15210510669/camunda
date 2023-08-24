/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {ProcessDto} from 'modules/api/incidents/fetchIncidentsByError';
import {Li, LinkWrapper} from '../styled';
import {InstancesBar} from 'modules/components/InstancesBar';

type Props = {
  errorMessage: string;
  processes: ProcessDto[];
  tabIndex?: number;
};

const Details: React.FC<Props> = ({errorMessage, processes, tabIndex}) => {
  return (
    <ul>
      {processes.map((item) => {
        const name = item.name || item.bpmnProcessId;

        return (
          <Li key={item.processId}>
            <LinkWrapper
              tabIndex={tabIndex ?? 0}
              to={Locations.processes({
                process: item.bpmnProcessId,
                version: item.version.toString(),
                errorMessage: truncateErrorMessage(errorMessage),
                incidents: true,
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-single-version',
                });
              }}
              title={getAccordionItemTitle(
                name,
                item.instancesWithActiveIncidentsCount,
                item.version,
                errorMessage,
              )}
            >
              <InstancesBar
                label={{
                  type: 'incident',
                  size: 'small',
                  text: getAccordionItemLabel(name, item.version),
                }}
                incidentsCount={item.instancesWithActiveIncidentsCount}
                size="small"
              />
            </LinkWrapper>
          </Li>
        );
      })}
    </ul>
  );
};

export {Details};
