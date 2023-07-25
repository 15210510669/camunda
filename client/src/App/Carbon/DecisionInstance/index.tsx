/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {useParams} from 'react-router-dom';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdDataStore} from 'modules/stores/drdData';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {PAGE_TITLE} from 'modules/constants';
import {tracking} from 'modules/tracking';
import {InstanceDetail} from '../Layout/InstanceDetail';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {Forbidden} from 'modules/components/Carbon/Forbidden';
import {DrdPanel} from './DrdPanel';
import {drdStore} from 'modules/stores/drd';
import {DecisionInstanceContainer} from './styled';
import {Drd} from './Drd';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const decisionName = decisionInstance?.decisionName;

  useEffect(() => {
    drdDataStore.init();
    decisionXmlStore.init();

    return () => {
      decisionInstanceDetailsStore.reset();
      drdDataStore.reset();
      decisionXmlStore.reset();
    };
  }, []);

  useEffect(() => {
    decisionInstanceDetailsStore.fetchDecisionInstance(decisionInstanceId);
  }, [decisionInstanceId]);

  useEffect(() => {
    if (decisionInstanceId !== '' && decisionName !== undefined)
      document.title = PAGE_TITLE.DECISION_INSTANCE(
        decisionInstanceId,
        decisionName,
      );
  }, [decisionInstanceId, decisionName]);

  useEffect(() => {
    if (decisionInstance !== null) {
      tracking.track({
        eventName: 'decision-instance-details-loaded',
        state: decisionInstance.state,
      });
    }
  }, [decisionInstance]);

  if (decisionInstanceDetailsStore.state.status === 'forbidden') {
    return <Forbidden />;
  }

  if (drdStore.state.panelState === 'maximized') {
    return <Drd />;
  }

  return (
    <>
      <VisuallyHiddenH1>Operate Decision Instance</VisuallyHiddenH1>
      <DecisionInstanceContainer>
        <InstanceDetail
          header={<Header />}
          topPanel={<DecisionPanel />}
          bottomPanel={<VariablesPanel />}
          type="decision"
          rightPanel={
            drdStore.state.panelState === 'minimized' ? (
              <DrdPanel>
                <Drd />
              </DrdPanel>
            ) : null
          }
        />
      </DecisionInstanceContainer>
    </>
  );
});

export {DecisionInstance};
