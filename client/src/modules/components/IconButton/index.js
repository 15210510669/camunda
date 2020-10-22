/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';
import {SIZES} from './constants';

const IconButton = React.forwardRef(function ExpandButton(
  {children, iconButtonTheme, icon, size, ...props},
  ref
) {
  return (
    <Styled.Button {...props} iconButtonTheme={iconButtonTheme} ref={ref}>
      <Styled.Icon size={size} iconButtonTheme={iconButtonTheme}>
        {icon}
      </Styled.Icon>
      {children}
    </Styled.Button>
  );
});

IconButton.propTypes = {
  iconButtonTheme: PropTypes.oneOf(['default', 'incidentsBanner', 'foldable'])
    .isRequired,
  size: PropTypes.oneOf(Object.keys(SIZES)),
  children: PropTypes.node,
  icon: PropTypes.node,
};

export default IconButton;
