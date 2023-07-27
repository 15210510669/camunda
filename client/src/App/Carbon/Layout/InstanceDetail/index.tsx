/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/Carbon/ResizablePanel';
import {Container} from './styled';
import {observer} from 'mobx-react';
import {useEffect, useRef, useState} from 'react';
import {Loading} from '@carbon/react';

type Props = {
  header: React.ReactNode;
  frameHeader?: React.ReactNode;
  frameFooter?: React.ReactNode;
  breadcrumb?: React.ReactNode;
  topPanel: React.ReactNode;
  bottomPanel: React.ReactNode;
  rightPanel?: React.ReactNode;
  type: 'process' | 'decision';
  hasLoadingOverlay?: boolean;
};
const InstanceDetail: React.FC<Props> = observer(
  ({
    type,
    header,
    breadcrumb,
    frameHeader,
    frameFooter,
    topPanel,
    bottomPanel,
    rightPanel,
    hasLoadingOverlay,
  }) => {
    const [clientHeight, setClientHeight] = useState(0);
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      setClientHeight(containerRef?.current?.clientHeight ?? 0);
    }, []);

    const panelMinHeight = clientHeight / 4;

    return (
      <Container
        $hasBreadcrumb={breadcrumb !== undefined}
        $hasFrameHeader={frameHeader !== undefined}
        $hasFrameFooter={frameFooter !== undefined}
      >
        {hasLoadingOverlay && <Loading data-testid="loading-overlay" />}
        {frameHeader}
        {breadcrumb}
        {header}
        <div ref={containerRef}>
          <ResizablePanel
            panelId={`${type}-detail-vertical-panel`}
            direction={SplitDirection.Vertical}
            minHeights={[panelMinHeight, panelMinHeight]}
          >
            {topPanel}
            {bottomPanel}
          </ResizablePanel>
        </div>
        {rightPanel}
        {frameFooter}
      </Container>
    );
  },
);

export {InstanceDetail};
