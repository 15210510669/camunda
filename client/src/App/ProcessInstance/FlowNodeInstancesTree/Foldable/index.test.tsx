/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';

import noop from 'lodash/noop';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {Foldable, Summary, Details} from './index';

describe('<Foldable />', () => {
  it('should show the unfoldable content', () => {
    const mockContent = 'mock-content';

    render(
      <Foldable isFoldable={false} isFolded={false}>
        <Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Summary>
      </Foldable>,
      {wrapper: ThemeProvider},
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
  });

  it('should handle content click', async () => {
    const mockContent = 'mock-content';
    const mockOnSelection = jest.fn();

    const {user} = render(
      <Foldable isFoldable={false} isFolded={false}>
        <Summary
          onSelection={mockOnSelection}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Summary>
      </Foldable>,
      {wrapper: ThemeProvider},
    );

    await user.click(screen.getByText(mockContent));

    expect(mockOnSelection).toHaveBeenCalled();
  });

  it('should show details', () => {
    const mockContent = 'mock-content';
    const mockDetails = 'mock-details';
    const mockNodeName = 'node-name';

    render(
      <Foldable isFoldable={true} isFolded={false}>
        <Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName={mockNodeName}
        >
          {mockContent}
        </Summary>
        <Details>{mockDetails}</Details>
      </Foldable>,
      {wrapper: ThemeProvider},
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
    expect(screen.getByText(mockDetails)).toBeInTheDocument();
  });

  it('should handle unfolded details', () => {
    const mockContent = 'mock-content';
    const mockDetails = 'mock-details';

    render(
      <Foldable isFoldable={true} isFolded={false}>
        <Summary
          onSelection={noop}
          isSelected={false}
          isLastChild={false}
          nodeName="node-name"
        >
          {mockContent}
        </Summary>
        <Details>{mockDetails}</Details>
      </Foldable>,
      {wrapper: ThemeProvider},
    );

    expect(screen.getByText(mockContent)).toBeInTheDocument();
    expect(screen.getByText(mockDetails)).toBeInTheDocument();
  });
});
