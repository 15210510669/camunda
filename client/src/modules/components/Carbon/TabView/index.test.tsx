/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {TabView} from './index';

describe('TabView', () => {
  it('should render panel header if there is only one tab', () => {
    render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
        ]}
      />,
      {wrapper: ThemeProvider}
    );

    expect(
      screen.getByRole('heading', {name: 'First Tab'})
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'First Tab'})
    ).not.toBeInTheDocument();
    expect(screen.getByText('Content of the first tab')).toBeInTheDocument();
  });

  it('should render first tab by default', () => {
    render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
          {
            id: 'tab-2',
            label: 'Second Tab',
            content: <div>Content of the second tab</div>,
          },
        ]}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('tab', {name: 'First Tab'})).toBeInTheDocument();
    expect(screen.getByRole('tab', {name: 'Second Tab'})).toBeInTheDocument();

    expect(screen.getByText('Content of the first tab')).toBeVisible();
  });

  it('should switch between tabs', async () => {
    const {user} = render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
          {
            id: 'tab-2',
            label: 'Second Tab',
            content: <div>Content of the second tab</div>,
          },
        ]}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('Content of the first tab')).toBeVisible();

    await user.click(screen.getByRole('tab', {name: 'Second Tab'}));
    expect(screen.queryByText('Content of the first tab')).not.toBeVisible();
    expect(screen.getByText('Content of the second tab')).toBeVisible();

    await user.click(screen.getByRole('tab', {name: 'First Tab'}));
    expect(screen.getByText('Content of the first tab')).toBeVisible();
    expect(screen.queryByText('Content of the second tab')).not.toBeVisible();
  });
});
