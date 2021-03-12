/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './Button.scss';

export default React.forwardRef(function Button(
  {active, main, primary, warning, icon, small, className, link, ...props},
  ref
) {
  return (
    <button
      type="button"
      {...props}
      className={classnames(className, 'Button', {
        primary,
        main,
        warning,
        icon,
        small,
        link,
        active,
      })}
      ref={ref}
    />
  );
});
