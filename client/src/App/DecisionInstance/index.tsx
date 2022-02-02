/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {useParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {DrdPanel} from './DrdPanel';
import {Container} from './styled';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId} = useParams<{decisionInstanceId: string}>();

  useEffect(() => {
    decisionInstanceStore.init(decisionInstanceId);
    return () => {
      decisionInstanceStore.reset();
    };
  }, [decisionInstanceId]);

  return (
    <Container>
      <Header />
      <DecisionPanel />
      <VariablesPanel />
      <DrdPanel />
    </Container>
  );
});

export {DecisionInstance};
