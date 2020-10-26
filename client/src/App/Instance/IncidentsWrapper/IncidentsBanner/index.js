/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {EXPAND_STATE} from 'modules/constants';
import * as Styled from './styled';
import {useParams} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';

const IncidentsBanner = observer(({onClick, isOpen, expandState}) => {
  const {id} = useParams();
  const {incidentsCount} = incidentsStore;

  const errorMessage = `There ${
    incidentsCount === 1 ? 'is' : 'are'
  } ${pluralSuffix(incidentsCount, 'Incident')} in Instance ${id}. `;
  const title = `View ${pluralSuffix(
    incidentsCount,
    'Incident'
  )} in Instance ${id}. `;

  return (
    expandState !== EXPAND_STATE.COLLAPSED && (
      <Styled.IncidentsBanner
        data-testid="incidents-banner"
        onClick={onClick}
        title={title}
        isExpanded={isOpen}
        iconButtonTheme="incidentsBanner"
      >
        {errorMessage}
      </Styled.IncidentsBanner>
    )
  );
});

IncidentsBanner.propTypes = {
  onClick: PropTypes.func,
  isOpen: PropTypes.bool,
  expandState: PropTypes.string,
};

export {IncidentsBanner};
