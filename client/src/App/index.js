/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {HashRouter as Router, Route, Switch} from 'react-router-dom';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import Authentication from './Authentication';
import Header from './Header';
import {Login} from './Login';
import Dashboard from './Dashboard';
import InstancesContainer from './Instances/InstancesContainer';
import Instance from './Instance';
import GlobalStyles from './GlobalStyles';
import {DataManagerProvider} from 'modules/DataManager';
import {InstanceSelectionProvider} from 'modules/contexts/InstanceSelectionContext';

function App() {
  return (
    <ThemeProvider>
      <DataManagerProvider>
        <CollapsablePanelProvider>
          <GlobalStyles />
          <Router>
            <Switch>
              <Route path="/login" component={Login} />
              <Authentication>
                <InstanceSelectionProvider>
                  <Header />
                  <Route exact path="/" component={Dashboard} />
                  <Route
                    exact
                    path="/instances"
                    component={InstancesContainer}
                  />
                  <Route exact path="/instances/:id" component={Instance} />
                </InstanceSelectionProvider>
              </Authentication>
            </Switch>
          </Router>
        </CollapsablePanelProvider>
      </DataManagerProvider>
    </ThemeProvider>
  );
}

export {App};
