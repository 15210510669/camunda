/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Bar} from './index';
import {
  mockStartNode,
  mockStartMetaData,
  mockMultiInstanceBodyNode,
  mockMultiInstanceBodyMetaData,
} from './index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';

describe('<Bar />', () => {
  afterEach(() => {
    flowNodeTimeStampStore.reset();
  });

  it('should show an icon based on node type and the node name', () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        metaData={mockStartMetaData}
        isBold={false}
        isSelected={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByTestId(`flow-node-icon-${mockStartMetaData.type.elementType}`)
    ).toBeInTheDocument();
    expect(screen.getByText(mockStartMetaData.name)).toBeInTheDocument();
  });

  it('should show the correct name for multi instance nodes', () => {
    render(
      <Bar
        flowNodeInstance={mockMultiInstanceBodyNode}
        metaData={mockMultiInstanceBodyMetaData}
        isBold={false}
        isSelected={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText(`${mockMultiInstanceBodyMetaData.name} (Multi Instance)`)
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', () => {
    render(
      <Bar
        flowNodeInstance={mockMultiInstanceBodyNode}
        metaData={mockMultiInstanceBodyMetaData}
        isBold={false}
        isSelected={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.queryByText(mockMultiInstanceBodyNode.endDate!)
    ).not.toBeInTheDocument();

    flowNodeTimeStampStore.toggleTimeStampVisibility();

    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
  });
});
