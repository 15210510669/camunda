/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {MultiRow, Container, Circle, Block} from './styled';

export function Row() {
  return (
    <Container data-testid="flow-node-instance-log-skeleton-row">
      <Circle />
      <Block />
    </Container>
  );
}

const Skeleton = React.memo((props) => {
  return <MultiRow Component={Row} {...props} />;
});

export {Skeleton};
