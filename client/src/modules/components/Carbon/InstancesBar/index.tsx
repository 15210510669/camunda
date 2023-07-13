/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {
  Wrapper,
  IncidentsCount,
  Label,
  BarContainer,
  ActiveInstancesBar,
  ActiveCount,
  IncidentsBar,
} from './styled';

type Props = {
  label?: {
    type: 'process' | 'incident';
    size: 'small' | 'medium';
    text: string;
  };
  activeInstancesCount?: number;
  incidentsCount: number;
  size: 'small' | 'medium' | 'large';
  className?: string;
};

const InstancesBar: React.FC<Props> = ({
  label,
  activeInstancesCount,
  incidentsCount,
  size,
  className,
}) => {
  const incidentsBarRatio =
    (100 * incidentsCount) / ((activeInstancesCount ?? 0) + incidentsCount);

  const hasIncidents = incidentsCount > 0;
  const hasActiveInstances = (activeInstancesCount ?? 0) > 0;
  const showIncidentsBar = activeInstancesCount !== undefined;

  return (
    <div className={className}>
      <Wrapper $size={size}>
        <IncidentsCount
          data-testid="incident-instances-badge"
          hasIncidents={hasIncidents}
        >
          {incidentsCount}
        </IncidentsCount>
        {label && (
          <Label
            $size={label.size}
            $isRed={label.type === 'incident' && size === 'medium'}
          >
            {label.text}
          </Label>
        )}

        {activeInstancesCount !== undefined && activeInstancesCount >= 0 && (
          <ActiveCount
            data-testid="active-instances-badge"
            $hasActiveInstances={hasActiveInstances}
          >
            {activeInstancesCount}
          </ActiveCount>
        )}
      </Wrapper>
      {showIncidentsBar && (
        <BarContainer>
          <ActiveInstancesBar $isPassive={!hasActiveInstances} />
          <IncidentsBar
            style={{
              width: `${incidentsBarRatio}%`,
            }}
          />
        </BarContainer>
      )}
    </div>
  );
};

export {InstancesBar};
