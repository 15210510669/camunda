/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen} from '@testing-library/react';

import {Panel} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';

describe('<Panel />', () => {
  it('should show the content and the title', () => {
    const mockTitle = 'Mock title';
    const mockContent = 'content';

    render(<Panel title={mockTitle}>{mockContent}</Panel>, {
      wrapper: MockThemeProvider,
    });

    expect(screen.getByText(mockTitle)).toBeInTheDocument();
    expect(screen.getByText(mockContent)).toBeInTheDocument();
  });

  it('should show and hide the footer', () => {
    const mockFooter = 'copyright notice';

    const {rerender} = render(
      <Panel title="title" footer={mockFooter}>
        content
      </Panel>,
      {
        wrapper: MockThemeProvider,
      },
    );

    expect(screen.getByText(mockFooter)).toBeInTheDocument();

    rerender(<Panel title="title">content</Panel>);

    expect(screen.queryByText(mockFooter)).not.toBeInTheDocument();
  });
});
