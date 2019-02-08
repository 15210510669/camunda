import React from 'react';
import classnames from 'classnames';

import './ProgressBar.scss';

import {numberParser} from 'services';
const {isNonNegativeNumber} = numberParser;

export default function ProgressBar({min, max, value, formatter, precision}) {
  const isInvalid = !isNonNegativeNumber(min) || !isNonNegativeNumber(max) || +max < +min;

  let relative, goalPercentage, goalExceeded, rightLabel, leftLabel;

  if (isInvalid) {
    relative = 0;
    goalPercentage = 0;
    goalExceeded = false;
    leftLabel = rightLabel = 'Invalid Configuration';
  } else {
    relative = Math.min(1, Math.max(0, (value - min) / (max - min)));
    goalPercentage = ((max - min) * 100) / (value - min);
    goalExceeded = max < value;
    leftLabel = formatter(min);
    rightLabel = goalExceeded ? formatter(value, precision) : 'Goal ' + formatter(max);
  }

  return (
    <div className="ProgressBar">
      {goalExceeded && (
        <div
          className="goalOverlay"
          style={{
            width: `${goalPercentage}%`
          }}
        >
          <span className={classnames('goalLabel', {rightSide: goalPercentage > 50})}>
            Goal {formatter(max)}
          </span>
        </div>
      )}
      <div className="progressLabel">{formatter(value, precision)}</div>
      <div
        className={classnames('filledOverlay', {goalExceeded})}
        style={{width: `${relative * 100}%`}}
      />
      <div className={classnames('rangeLabels')}>
        {leftLabel}
        <span className="rightLabel">{rightLabel}</span>
      </div>
    </div>
  );
}
