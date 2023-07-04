/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {JSONViewer} from './JSONViewer/index';
import {Container} from './styled';
import {EmptyMessage} from 'modules/components/Carbon/EmptyMessage';
import {ErrorMessage} from 'modules/components/Carbon/ErrorMessage';
import {Loading} from '@carbon/react';

const Result: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;

  return (
    <Container>
      {status === 'initial' && <Loading data-testid="result-loading-spinner" />}
      {status === 'fetched' &&
        decisionInstance !== null &&
        decisionInstance.state !== 'FAILED' && (
          <JSONViewer
            data-testid="results-json-viewer"
            value={decisionInstance.result ?? '{}'}
          />
        )}
      {status === 'fetched' && decisionInstance?.state === 'FAILED' && (
        <EmptyMessage message="No result available because the evaluation failed" />
      )}
      {status === 'error' && <ErrorMessage />}
    </Container>
  );
});

export {Result};
