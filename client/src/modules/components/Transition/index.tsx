/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children} from 'react';
import {CSSTransition, TransitionGroup} from 'react-transition-group';

type Props = {
  children: React.ReactNode;
};

function Transition(props: Props) {
  return (
    props.children && (
      // @ts-expect-error
      <CSSTransition classNames="transition" {...props}>
        {Children.only(props.children)}
      </CSSTransition>
    )
  );
}

export {Transition, TransitionGroup};
