/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {BrowserRouter, Route, Switch} from 'react-router-dom';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {NotificationProvider} from 'modules/notifications';

import Authentication from './Authentication';
import {Header} from './Header';
import {Login} from './Login';
import {Dashboard} from './Dashboard';
import {Instances} from './Instances';
import {Instance} from './Instance';
import {Decisions} from './Decisions';
import GlobalStyles from './GlobalStyles';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {GettingStartedExperience} from './GettingStartedExperience';
import {CommonUiContext} from 'modules/CommonUiContext';
import {Routes} from 'modules/routes';
import {HashRouterMigrator} from './HashRouterMigrator';
import {IS_DMN} from 'modules/feature-flags';

function App() {
  return (
    <ThemeProvider>
      <NotificationProvider>
        <GlobalStyles />
        <NetworkStatusWatcher />
        <CommonUiContext />
        <BrowserRouter basename={window.clientConfig?.contextPath ?? '/'}>
          <GettingStartedExperience />
          <HashRouterMigrator />
          <Switch>
            <Route path={Routes.login()} component={Login} />
            <Authentication>
              <Header />
              <Route exact path={Routes.dashboard()} component={Dashboard} />
              <Route exact path={Routes.instances()} component={Instances} />
              <Route exact path={Routes.instance()} component={Instance} />
              {IS_DMN && (
                <Route exact path={Routes.decisions()} component={Decisions} />
              )}
            </Authentication>
          </Switch>
        </BrowserRouter>
      </NotificationProvider>
    </ThemeProvider>
  );
}

export {App};
