/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link} from 'modules/components/Link';
import {Paths, Locations} from 'modules/routes';
import {DecisionInstanceDto} from 'modules/api/decisionInstances/fetchDecisionInstance';
import {tracking} from 'modules/tracking';
import {formatDate} from 'modules/utils/date/formatDate';
import {useParams} from 'react-router-dom';
import {Table, TD, TH, SkeletonBlock} from './styled';

type Props = {
  decisionInstance?: DecisionInstanceDto;
  'data-testid'?: string;
};

const Details: React.FC<Props> = ({decisionInstance, ...props}) => {
  const {decisionInstanceId} = useParams<{decisionInstanceId: string}>();

  return (
    <Table data-testid={props['data-testid']}>
      <thead>
        <tr>
          <TH>Decision Name</TH>
          <TH>Decision Instance Key</TH>
          <TH>Version</TH>
          <TH>Evaluation Date</TH>
          <TH>Process Instance Key</TH>
        </tr>
      </thead>
      <tbody>
        {decisionInstance === undefined ? (
          <tr>
            <TD>
              <SkeletonBlock $width="200px" />
            </TD>
            <TD>
              <SkeletonBlock $width="162px" />
            </TD>
            <TD>
              <SkeletonBlock $width="17px" />
            </TD>
            <TD>
              <SkeletonBlock $width="151px" />
            </TD>
            <TD>
              <SkeletonBlock $width="162px" />
            </TD>
          </tr>
        ) : (
          <tr>
            <TD title={decisionInstance.decisionName}>
              {decisionInstance.decisionName}
            </TD>
            <TD title={decisionInstanceId}>{decisionInstanceId}</TD>
            <TD>
              <Link
                to={Locations.decisions({
                  version: decisionInstance.decisionVersion.toString(),
                  name: decisionInstance.decisionId,
                  evaluated: true,
                  failed: true,
                })}
                title={`View decision ${decisionInstance.decisionName} version ${decisionInstance.decisionVersion} instances`}
                onClick={() => {
                  tracking.track({
                    eventName: 'navigation',
                    link: 'decision-details-version',
                  });
                }}
              >
                {decisionInstance.decisionVersion}
              </Link>
            </TD>
            <TD title={formatDate(decisionInstance.evaluationDate) ?? '--'}>
              {formatDate(decisionInstance.evaluationDate)}
            </TD>
            <TD title={decisionInstance.processInstanceId ?? 'None'}>
              {decisionInstance.processInstanceId ? (
                <Link
                  to={Paths.processInstance(decisionInstance.processInstanceId)}
                  title={`View process instance ${decisionInstance.processInstanceId}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-details-parent-process-details',
                    });
                  }}
                >
                  {decisionInstance.processInstanceId}
                </Link>
              ) : (
                'None'
              )}
            </TD>
          </tr>
        )}
      </tbody>
    </Table>
  );
};

export {Details};
