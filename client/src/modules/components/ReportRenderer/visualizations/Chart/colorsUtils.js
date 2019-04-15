/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';
const {convertToMilliseconds} = formatters;

const darkColors = {
  bar: '#1991c8',
  targetBar: '#A62A31',
  grid: 'rgba(255,255,255,0.1)',
  label: '#ddd',
  border: '#333'
};

const lightColors = {
  bar: '#1991c8',
  targetBar: '#A62A31',
  grid: 'rgba(0,0,0,0.1)',
  label: '#666',
  border: '#fff'
};

const numberOfStripes = 100;

export function getColorFor(type, isDark) {
  if (isDark) {
    return darkColors[type];
  } else {
    return lightColors[type];
  }
}

export function createColors(amount, isDark) {
  const colors = [];
  // added an offset of 50 to avoid red colors
  const offset = 50;
  const startValue = offset;
  const stopValue = 360 - offset;
  const stepSize = ~~((stopValue - startValue) / amount);
  for (let i = 0; i < amount; i++) {
    colors.push(`hsl(${i * stepSize + offset}, 65%, ${isDark ? 40 : 50}%)`);
  }
  return colors;
}

export function determineBarColor({unit, value, isBelow}, data, datasetColor, isStriped, isDark) {
  const barValue = unit ? convertToMilliseconds(value, unit) : value;

  const targetColor = isStriped ? getStripedColor(datasetColor) : getColorFor('targetBar', isDark);

  return data.map(({value}) => {
    if (isBelow) {
      return value < barValue ? datasetColor : targetColor;
    } else {
      return value >= barValue ? datasetColor : targetColor;
    }
  });
}

function getStripedColor(color) {
  const container = document.createElement('canvas');
  const ctx = container.getContext('2d');
  const defaultCanvasWidth = 300;

  // we multiply by 2 here to make the moveto reach x=0 at the end of the loop
  // since we are shifting the stripes to the left by canvaswidth
  for (let i = 0; i < numberOfStripes * 2; i++) {
    const thickness = defaultCanvasWidth / numberOfStripes;
    ctx.beginPath();
    ctx.strokeStyle = i % 2 ? 'transparent' : color;
    ctx.lineWidth = thickness;
    ctx.lineCap = 'round';

    // shift the starting point to the left by defaultCanvasWidth to make lines diagonal
    ctx.moveTo(i * thickness + thickness / 2 - defaultCanvasWidth, 0);
    ctx.lineTo(i * thickness + thickness / 2, defaultCanvasWidth);
    ctx.stroke();
  }

  return ctx.createPattern(container, 'repeat');
}
