/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Icon} from 'components';
import './DropdownOption.scss';
import classnames from 'classnames';

export default React.forwardRef(function DropdownOption({active, link, ...props}, ref) {
  const commonProps = {
    ...props,
    className: classnames('DropdownOption', props.className, {'is-active': active}),
    tabIndex: props.disabled ? '-1' : '0',
    ref,
  };

  const content = (
    <>
      {props.checked && <Icon className="checkMark" type="check-small" size="10px" />}
      {props.children}
    </>
  );

  if (link) {
    return (
      <Link {...commonProps} to={link}>
        {content}
      </Link>
    );
  }
  return (
    <div {...commonProps} onClick={(evt) => !props.disabled && props.onClick && props.onClick(evt)}>
      {content}
    </div>
  );
});
