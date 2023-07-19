/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Link, MemoryRouter} from 'react-router-dom';
import {ListFooter} from '../ListFooter';
import {createInstance, createOperation} from 'modules/testUtils';
import {useEffect} from 'react';
import {processInstancesStore} from 'modules/stores/processInstances';
import {panelStatesStore} from 'modules/stores/panelStates';
import {authenticationStore} from 'modules/stores/authentication';
import {processesStore} from 'modules/stores/processes';
import {LegacyPaths} from 'modules/legacyRoutes';

function createWrapper(initialPath: string = LegacyPaths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesStore.reset();
        panelStatesStore.reset();
        authenticationStore.reset();
        processesStore.reset();
      };
    }, []);
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <ListFooter />
          <Link
            to={`${LegacyPaths.processes()}?incidents=true&active=true&process=bigVarProcess`}
          >
            go to big var
          </Link>
        </MemoryRouter>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});

const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export {createWrapper, INSTANCE, ACTIVE_INSTANCE};
