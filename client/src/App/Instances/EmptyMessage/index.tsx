/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  message: string | React.ReactNode;
};

export default function EmptyMessage({message, ...props}: Props) {
  return (
    <Styled.EmptyMessage {...props}>
      {typeof message === 'string'
        ? message
            .split('\n')
            .map((item, index) => <span key={index}>{item}</span>)
        : message}
    </Styled.EmptyMessage>
  );
}
