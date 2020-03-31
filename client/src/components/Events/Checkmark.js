/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState} from 'react';
import classnames from 'classnames';
import {Icon} from 'components';

import './Checkmark.scss';

export default function Checkmark({event: {eventLabel, eventName, source}}) {
  const checkMark = useRef(null);
  const [tooltipPosition, setTooltipPosition] = useState('top');

  function calculateTooltipPosition() {
    const topDistance = checkMark.current.getBoundingClientRect().top;
    const svgTopDistance = checkMark.current.closest('.djs-container').getBoundingClientRect().top;
    if (topDistance - svgTopDistance < 70) {
      setTooltipPosition('bottom');
    } else {
      setTooltipPosition('top');
    }
  }

  return (
    <div className="Checkmark" ref={checkMark}>
      <Icon type="check-circle" size="18" onMouseOver={calculateTooltipPosition} />
      <div className={classnames('Tooltip light', tooltipPosition)}>
        <div className={`Tooltip__text-${tooltipPosition}`}>
          <span>{eventLabel || eventName}</span>
          <br /> source: {source}
        </div>
      </div>
    </div>
  );
}
