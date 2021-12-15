/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Disclaimer} from './index';

const DISCLAIMER_TEXT =
  'Non-Production License. If you would like information on production usage, please refer to our terms & conditions page or contact sales.';

describe('<Disclaimer />', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should show the disclaimer', () => {
    const {rerender} = render(<Disclaimer />, {wrapper: ThemeProvider});

    // we need this custom selector because the text contains a link
    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'})
    ).toHaveAttribute(
      'href',
      'https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/'
    );
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/'
    );

    window.clientConfig = {
      isEnterprise: false,
    };
    rerender(<Disclaimer />);

    expect(
      screen.getByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'terms & conditions page'})
    ).toHaveAttribute(
      'href',
      'https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/'
    );
    expect(screen.getByRole('link', {name: 'contact sales'})).toHaveAttribute(
      'href',
      'https://camunda.com/contact/'
    );
  });

  it('should not render the disclaimer', () => {
    window.clientConfig = {
      isEnterprise: true,
    };
    render(<Disclaimer />);

    expect(
      screen.queryByText((content, element) => {
        return content !== '' && element?.textContent === DISCLAIMER_TEXT;
      })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'terms & conditions page'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'contact sales'})
    ).not.toBeInTheDocument();
  });
});
