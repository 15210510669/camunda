/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {
  Container,
  Panel,
  Title,
  StructuredList,
  EmptyMessage,
  ErrorMessage,
} from './styled';
import {Skeleton} from './Skeleton';

const inputMappingsColumns = [
  {
    cellContent: 'Name',
    width: '30%',
  },
  {
    cellContent: 'Value',
    width: '70%',
  },
];

const outputMappingsColumns = [
  {
    cellContent: 'Rule',
    width: '20%',
  },
  {
    cellContent: 'Name',
    width: '40%',
  },
  {
    cellContent: 'Value',
    width: '40%',
  },
];

const InputsAndOutputs: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;

  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientWidth, setClientWidth] = useState(0);

  useEffect(() => {
    setClientWidth(containerRef?.current?.clientWidth ?? 0);
  }, []);

  const panelMinWidth = clientWidth / 3;

  return (
    <Container ref={containerRef}>
      <ResizablePanel
        panelId="decision-instance-horizontal-panel"
        direction={SplitDirection.Horizontal}
        minWidths={[panelMinWidth, panelMinWidth]}
      >
        <Panel>
          {status !== 'error' && <Title>Inputs</Title>}
          {status === 'initial' && (
            <Skeleton
              dataTestId="inputs-skeleton"
              columnWidths={inputMappingsColumns.map(({width}) => width)}
            />
          )}
          {status === 'fetched' &&
            decisionInstance?.state === 'FAILED' &&
            decisionInstance?.evaluatedInputs.length === 0 && (
              <EmptyMessage message="No input available because the evaluation failed" />
            )}
          {status === 'fetched' &&
            decisionInstance?.evaluatedInputs !== undefined &&
            decisionInstance.evaluatedInputs.length > 0 && (
              <StructuredList
                label="Inputs"
                headerSize="sm"
                isFlush={false}
                headerColumns={inputMappingsColumns}
                rows={decisionInstance?.evaluatedInputs.map(
                  ({id, name, value}) => {
                    return {
                      key: id,
                      columns: [
                        {
                          cellContent: name,
                        },
                        {cellContent: value},
                      ],
                    };
                  },
                )}
              />
            )}
          {status === 'error' && <ErrorMessage />}
        </Panel>
        <Panel>
          {status !== 'error' && <Title>Outputs</Title>}
          {status === 'initial' && (
            <Skeleton
              dataTestId="outputs-skeleton"
              columnWidths={outputMappingsColumns.map(({width}) => width)}
            />
          )}
          {status === 'fetched' && decisionInstance?.state !== 'FAILED' && (
            <StructuredList
              label="Inputs"
              headerSize="sm"
              isFlush={false}
              headerColumns={outputMappingsColumns}
              rows={
                decisionInstance?.evaluatedOutputs.map(
                  ({ruleIndex, name, value}) => {
                    return {
                      key: ruleIndex.toString(),
                      columns: [
                        {
                          cellContent: ruleIndex,
                        },
                        {
                          cellContent: name,
                        },
                        {cellContent: value},
                      ],
                    };
                  },
                ) ?? []
              }
            />
          )}
          {status === 'error' && <ErrorMessage />}
          {decisionInstance?.state === 'FAILED' && (
            <EmptyMessage message="No output available because the evaluation failed" />
          )}
        </Panel>
      </ResizablePanel>
    </Container>
  );
});

export {InputsAndOutputs};
