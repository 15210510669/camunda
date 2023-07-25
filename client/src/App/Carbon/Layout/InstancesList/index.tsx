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
import {OperationsPanel} from 'modules/components/Carbon/OperationsPanel';

type Props = {
  filters: React.ReactNode;
  diagram: React.ReactNode;
  instances: React.ReactNode;
  type: 'process' | 'decision';
};
const InstancesList: React.FC<Props> = observer(
  ({filters, diagram, instances, type}) => {
    const [clientHeight, setClientHeight] = useState(0);
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      setClientHeight(containerRef?.current?.clientHeight ?? 0);
    }, []);

    const panelMinHeight = clientHeight / 4;

    return (
      <Container>
        <section>{filters}</section>
        <div ref={containerRef}>
          <ResizablePanel
            panelId={`${type}-instances-vertical-panel`}
            direction={SplitDirection.Vertical}
            minHeights={[panelMinHeight, panelMinHeight]}
          >
            {diagram}
            {instances}
          </ResizablePanel>
        </div>
        <OperationsPanel />
      </Container>
    );
  },
);

export {InstancesList};
