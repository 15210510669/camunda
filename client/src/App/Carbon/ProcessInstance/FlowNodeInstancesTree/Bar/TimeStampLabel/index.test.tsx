/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {TimeStampLabel} from './index';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {act} from 'react-dom/test-utils';

describe('TimeStampLabel', () => {
  it('should hide/display time stamp on time stamp toggle', async () => {
    render(<TimeStampLabel timeStamp={'2020-07-09T12:26:22.237+0000'} />);

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(await screen.findByText(MOCK_TIMESTAMP)).toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
  });
});
