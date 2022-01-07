/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Collapse} from '../Collapse';
import {PanelListItem} from '../PanelListItem';
import {
  concatTitle,
  concatLabel,
  concatGroupTitle,
  concatButtonTitle,
} from './service';

import * as Styled from './styled';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';
import {StatusMessage} from 'modules/components/StatusMessage';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {Locations} from 'modules/routes';
import truncate from 'lodash/truncate';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';

function truncateErrorMessage(errorMessage: string) {
  return truncate(errorMessage, {
    length: 100,
    separator: ' ',
    omission: '',
  });
}

const IncidentsByError = observer(() => {
  useEffect(() => {
    incidentsByErrorStore.getIncidentsByError();
    return () => {
      incidentsByErrorStore.reset();
    };
  }, []);

  const renderIncidentsPerProcess = (errorMessage: any, items: any) => {
    return (
      <Styled.VersionUl>
        {items.map((item: any) => {
          const name = item.name || item.bpmnProcessId;
          const title = concatTitle(
            name,
            item.instancesWithActiveIncidentsCount,
            item.version,
            errorMessage
          );
          const label = concatLabel(name, item.version);
          return (
            <Styled.VersionLi key={item.processId}>
              <PanelListItem
                to={(location) =>
                  Locations.filters(location, {
                    process: item.bpmnProcessId,
                    version: item.version,
                    errorMessage: truncateErrorMessage(errorMessage),
                    incidents: true,
                  })
                }
                onClick={() => {
                  panelStatesStore.expandFiltersPanel();
                  tracking.track({
                    eventName: 'navigation',
                    link: 'dashboard-incidents-by-error-single-process',
                  });
                }}
                title={title}
                $boxSize="small"
              >
                <Styled.VersionLiInstancesBar
                  label={label}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  barHeight={2}
                  size="small"
                />
              </PanelListItem>
            </Styled.VersionLi>
          );
        })}
      </Styled.VersionUl>
    );
  };

  const renderIncidentByError = (
    errorMessage: any,
    instancesWithErrorCount: any
  ) => {
    const title = concatGroupTitle(instancesWithErrorCount, errorMessage);

    return (
      <PanelListItem
        to={(location) =>
          Locations.filters(location, {
            errorMessage: truncateErrorMessage(errorMessage),
            incidents: true,
          })
        }
        onClick={() => {
          panelStatesStore.expandFiltersPanel();
          tracking.track({
            eventName: 'navigation',
            link: 'dashboard-incidents-by-error-all-processes',
          });
        }}
        title={title}
      >
        <Styled.LiInstancesBar
          label={errorMessage}
          incidentsCount={instancesWithErrorCount}
          size="medium"
          barHeight={2}
        />
      </PanelListItem>
    );
  };

  const {incidents, status} = incidentsByErrorStore.state;

  if (['initial', 'fetching'].includes(status)) {
    return <Skeleton />;
  }

  if (status === 'fetched' && incidents.length === 0) {
    return (
      <StatusMessage variant="success">
        There are no Instances with Incidents
      </StatusMessage>
    );
  }

  if (status === 'error') {
    return (
      <StatusMessage variant="error">
        Incidents by Error Message could not be fetched
      </StatusMessage>
    );
  }

  return (
    <ul data-testid="incidents-by-error">
      {incidents.map((item, index) => {
        const buttonTitle = concatButtonTitle(
          item.instancesWithErrorCount,
          item.errorMessage
        );

        return (
          <Styled.Li
            key={item.errorMessage}
            data-testid={`incident-byError-${index}`}
          >
            <Collapse
              content={renderIncidentsPerProcess(
                item.errorMessage,
                item.processes
              )}
              header={renderIncidentByError(
                item.errorMessage,
                item.instancesWithErrorCount
              )}
              buttonTitle={buttonTitle}
            />
          </Styled.Li>
        );
      })}
    </ul>
  );
});

export {IncidentsByError};
