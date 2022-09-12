/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Icon, DownloadButton} from 'components';
import {loadRawData, formatters} from 'services';
import {t} from 'translation';

export function InstancesButton({id, name, config, value, totalCount, user}) {
  return (
    <DownloadButton
      retriever={loadRawData({
        ...config,
        filter: [
          {
            type: 'flowNodeDuration',
            data: {[id]: {operator: '>', value, unit: 'millis'}},
            filterLevel: 'instance',
          },
        ],
        includedColumns: ['processInstanceId'],
      })}
      fileName={
        formatters.formatFileName(name || id) +
        '-' +
        t('analysis.outlier.tooltip.outlier.label-plural') +
        '.csv'
      }
      totalCount={totalCount}
      user={user}
    >
      <Icon size="14" type="save" />
      {t('common.instanceIds')}
    </DownloadButton>
  );
}

export default InstancesButton;
