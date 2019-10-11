/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export default class AutoRefreshBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.timer = setInterval(this.props.loadReportData, this.props.interval);
  }

  componentWillUnmount() {
    clearInterval(this.timer);
  }

  componentDidUpdate(prevProps) {
    if (
      prevProps.interval !== this.props.interval ||
      prevProps.loadReportData !== this.props.loadReportData
    ) {
      clearInterval(this.timer);
      this.timer = setInterval(this.props.loadReportData, this.props.interval);
    }
  }
}
