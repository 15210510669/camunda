/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';
import {Route} from 'react-router-dom';

import {Pages} from 'modules/constants/pages';
import {Header} from './Header';
import {Filters} from './Filters';
import {Tasks} from './Tasks';
import {Details} from './Details';
import {Variables} from './Variables';
import {
  Container,
  TasksPanel,
  DetailsPanel,
  NoTaskSelectedMessage,
} from './styled';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';

const Tasklist: React.FC = () => {
  return (
    <>
      <Header />
      <Container>
        <TasksPanel title="Tasks" hasTransparentBackground>
          <Filters />
          <Tasks />
        </TasksPanel>
        <DetailsPanel
          title="Details"
          footer={getCurrentCopyrightNoticeText()}
          hasRoundTopLeftCorner
        >
          <Route exact path={Pages.Initial()}>
            <NoTaskSelectedMessage>
              Select a task to see the details.
            </NoTaskSelectedMessage>
          </Route>
          <Route path={Pages.TaskDetails()}>
            <Details />
            <Variables />
          </Route>
        </DetailsPanel>
      </Container>
    </>
  );
};

export {Tasklist};
