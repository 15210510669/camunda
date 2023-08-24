/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {observer} from 'mobx-react-lite';
import {useLocation} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {authenticationStore} from 'modules/stores/authentication';
import {notificationsStore} from 'modules/stores/carbonNotifications';

const SessionWatcher: React.FC = observer(() => {
  const removeNotification = useRef<(() => void) | null>(null);
  const {status} = authenticationStore.state;
  const location = useLocation();

  useEffect(() => {
    async function handleSessionExpiration() {
      removeNotification.current = notificationsStore.displayNotification({
        kind: 'info',
        title: 'Session expired',
        isDismissable: true,
      });
    }

    if (
      removeNotification.current === null &&
      location.pathname !== Paths.login() &&
      (status === 'session-expired' ||
        (location.pathname !== Paths.dashboard() &&
          status === 'invalid-initial-session'))
    ) {
      handleSessionExpiration();
    }

    if (['logged-in', 'user-information-fetched'].includes(status)) {
      removeNotification.current?.();
    }
  }, [status, location.pathname]);

  return null;
});

export {SessionWatcher};
