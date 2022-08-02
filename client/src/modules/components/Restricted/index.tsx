/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {authenticationStore, Permissions} from 'modules/stores/authentication';
import {observer} from 'mobx-react';

type Props = {
  children: React.ReactNode;
  scopes: Permissions;
  fallback?: React.ReactNode;
};

const Restricted: React.FC<Props> = observer(({children, scopes, fallback}) => {
  if (!authenticationStore.hasPermission(scopes)) {
    return fallback ? <>{fallback}</> : null;
  }

  return <>{children}</>;
});

export {Restricted};
