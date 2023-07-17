/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {DecisionState} from '.';

describe('<DecisionState />', () => {
  it('should render evaluated state', () => {
    const {container} = render(<div />);

    render(<DecisionState state="EVALUATED" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-completed.svg'),
    ).toBeInTheDocument();
  });

  it('should render failed state', () => {
    const {container} = render(<div />);

    render(<DecisionState state="FAILED" container={container} />);

    expect(
      screen.getByText('diagram-badge-dmn-incident.svg'),
    ).toBeInTheDocument();
  });
});
