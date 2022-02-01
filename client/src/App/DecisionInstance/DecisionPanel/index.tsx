/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useRef} from 'react';
import {autorun} from 'mobx';
import {observer} from 'mobx-react';
import {DecisionViewer} from 'modules/dmn-js/DecisionViewer';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {Container} from './styled';

const DecisionPanel: React.FC = observer(() => {
  const decisionViewer = useRef<DecisionViewer | null>(null);
  const decisionViewerRef = useRef<HTMLDivElement | null>(null);

  if (decisionViewer.current === null) {
    decisionViewer.current = new DecisionViewer();
  }

  useEffect(() => {
    autorun(() => {
      if (
        decisionViewerRef.current !== null &&
        decisionXmlStore.state.xml !== null &&
        decisionInstanceStore.state.decisionInstance !== null
      ) {
        decisionViewer.current!.render(
          decisionViewerRef.current,
          decisionXmlStore.state.xml,
          decisionInstanceStore.state.decisionInstance.decisionId
        );
      }
    });

    return () => {
      decisionViewer.current?.reset();
    };
  }, []);

  return <Container ref={decisionViewerRef} />;
});

export {DecisionPanel};
