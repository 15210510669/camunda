/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, Icon} from 'components';

import './DeleteButton.scss';

export default function DeleteButton(props) {
  return (
    <Button
      className="DeleteButton"
      onClick={(event) => props.deleteReport({event, report: props.report})}
    >
      <Icon type="close-small" />
    </Button>
  );
}
