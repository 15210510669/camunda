import React from 'react';
import {HashRouter as Router, Route} from 'react-router-dom';

import {
  PrivateRoute,
  Header,
  Footer,
  Login,
  Home,
  Dashboards,
  Reports,
  Report,
  Dashboard,
  Analysis,
  Alerts,
  Sharing
} from './components';

import {ErrorBoundary} from 'components';

import {Provider as Theme} from 'theme';

const mainWrapped = Component => props => (
  <main>
    <ErrorBoundary>
      <Component {...props} />
    </ErrorBoundary>
  </main>
);

const headered = Component => props => {
  return (
    <React.Fragment>
      <Header name="Camunda Optimize" />
      <main>
        <ErrorBoundary>
          <Component {...props} />
        </ErrorBoundary>
      </main>
      <Footer />
    </React.Fragment>
  );
};

const App = () => (
  <Theme>
    <Router>
      <div className="Root-container">
        <Route exact path="/login" component={mainWrapped(Login)} />
        <PrivateRoute exact path="/" component={headered(Home)} />
        <PrivateRoute exact path="/dashboards" component={headered(Dashboards)} />
        <PrivateRoute exact path="/reports" component={headered(Reports)} />
        <PrivateRoute exact path="/analysis" component={headered(Analysis)} />
        <PrivateRoute exact path="/alerts" component={headered(Alerts)} />
        <Route exact path="/share/:type/:id" component={mainWrapped(Sharing)} />
        <PrivateRoute path="/report/:id/:viewMode?" component={headered(Report)} />
        <PrivateRoute path="/dashboard/:id/:viewMode?" component={headered(Dashboard)} />
      </div>
    </Router>
  </Theme>
);

export default App;
