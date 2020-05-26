/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {
  HeaderContent,
  BrandInfo,
  Brand,
  LogoIcon,
  UserControls,
} from './styled';

const Header: React.FC = () => {
  return (
    <HeaderContent>
      <BrandInfo>
        <Brand to="/">
          <LogoIcon data-testid="logo" />
          <div>Zeebe Tasklist</div>
        </Brand>
      </BrandInfo>
      <UserControls />
    </HeaderContent>
  );
};

export {Header};
