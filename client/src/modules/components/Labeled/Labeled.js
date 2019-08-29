/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';
import './Labeled.scss';

export default function Labeled({label, className, appendLabel, children, disabled, ...props}) {
  return (
    <div className={classnames('Labeled', className)} disabled={disabled}>
      <label onClick={catchClick} {...props}>
        {!appendLabel && <span className="label before">{label}</span>}
        {children}
        {appendLabel && <span className="label after">{label}</span>}
      </label>
    </div>
  );
}

function catchClick(evt) {
  if (!evt.target.classList.contains('label') && !evt.target.classList.contains('Input')) {
    evt.preventDefault();
  }
}
