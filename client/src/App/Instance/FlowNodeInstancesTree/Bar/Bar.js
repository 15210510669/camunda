/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {TYPE} from 'modules/constants';

import TimeStampLabel from '../TimeStampLabel';

import * as Styled from './styled';

const BarComponent = ({node, isSelected}) => {
  const name = `${node.name}${
    node.type === TYPE.MULTI_INSTANCE_BODY ? ` (Multi Instance)` : ''
  }`;

  return (
    <Styled.Bar showSelectionStyle={isSelected}>
      <Styled.NodeIcon
        types={node.typeDetails}
        isSelected={isSelected}
        data-test={`flowNodeIcon-${node.type}`}
      />
      <Styled.NodeName
        isSelected={isSelected}
        isBold={node.children.length > 0}
      >
        {name}
      </Styled.NodeName>
      <TimeStampLabel timeStamp={node.endDate} isSelected={isSelected} />
    </Styled.Bar>
  );
};

export default React.memo(BarComponent);
export const NoWrapBar = BarComponent;

BarComponent.propTypes = {
  node: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    typeDetails: PropTypes.object.isRequired,
    endDate: PropTypes.string,
    children: PropTypes.arrayOf(PropTypes.object),
  }),
  isSelected: PropTypes.bool,
};
