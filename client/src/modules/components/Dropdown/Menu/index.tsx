/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  onKeyDown: (...args: any[]) => any;
  placement?: 'top' | 'bottom';
  className?: string;
};

export default class Menu extends React.Component<Props> {
  render() {
    const {onKeyDown, placement, children, className} = this.props;

    return (
      <Styled.Ul
        $placement={placement}
        className={className}
        data-testid="menu"
      >
        {React.Children.map(children, (child, index) => (
          <Styled.Li onKeyDown={onKeyDown} $placement={placement} key={index}>
            {child}
          </Styled.Li>
        ))}
      </Styled.Ul>
    );
  }
}
