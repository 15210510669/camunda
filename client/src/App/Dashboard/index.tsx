/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './MetricPanel';
import {InstancesByProcess} from './InstancesByProcess';
import {IncidentsByError} from './IncidentsByError';
import {PAGE_TITLE} from 'modules/constants';
import Copyright from 'modules/components/Copyright';
import {
  Container,
  MetricPanelWrapper,
  TileWrapper,
  Tile,
  TileTitle,
  TileContent,
  Footer,
  Tiles,
} from './styled';

function Dashboard() {
  useEffect(() => {
    document.title = PAGE_TITLE.DASHBOARD;
  }, []);

  return (
    <Container>
      <VisuallyHiddenH1>Operate Dashboard</VisuallyHiddenH1>
      <Tiles>
        <MetricPanelWrapper>
          <MetricPanel />
        </MetricPanelWrapper>
        <TileWrapper>
          <Tile>
            <TileTitle>Instances by Process</TileTitle>
            <TileContent>
              <InstancesByProcess />
            </TileContent>
          </Tile>
          <Tile>
            <TileTitle>Incidents by Error Message</TileTitle>
            <TileContent>
              <IncidentsByError />
            </TileContent>
          </Tile>
        </TileWrapper>
      </Tiles>

      <Footer>
        <Copyright />
      </Footer>
    </Container>
  );
}

export {Dashboard};
