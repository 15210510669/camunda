/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import moment from 'moment';
import './zoomIn.scss';

export default ({updateReport, filters, type, valueRange: {min, max}}) => {
  let currentlyDragging = false;
  let currentPosition = null;
  let startPosition = null;
  let startMouse = null;
  let canvas = null;
  let triggered = 0;

  const selectionLeft = document.createElement('div');
  const selectionRight = document.createElement('div');
  selectionLeft.classList.add('selectionIndicator');
  selectionLeft.classList.add('left');
  selectionRight.classList.add('selectionIndicator');
  selectionRight.classList.add('right');

  function onHover({offsetX}) {
    const {left, right} = this.chartArea;

    if (offsetX >= left && offsetX <= right) {
      const position = ((offsetX - left) / (right - left)) * (max - min) + min;
      currentPosition = position;
    } else {
      currentPosition = null;
    }
  }

  function mousedown({offsetX}) {
    if (currentPosition) {
      currentlyDragging = true;
      startPosition = currentPosition;
      startMouse = offsetX;
      triggered = false;

      document.body.addEventListener('mousemove', mousemove);
      document.body.addEventListener('mouseup', mouseup);
    }
  }

  function mousemove({offsetX, target}) {
    if (currentlyDragging) {
      triggered |= Math.abs(offsetX - startMouse) > 5;

      if (triggered) {
        if (target === canvas && currentPosition) {
          selectionLeft.classList.add('active');
          selectionRight.classList.add('active');

          if (offsetX > startMouse) {
            selectionLeft.style.width = startMouse + 'px';
            selectionRight.style.left = offsetX + 'px';
          } else {
            selectionLeft.style.width = offsetX + 'px';
            selectionRight.style.left = startMouse + 'px';
          }
        } else {
          selectionLeft.classList.remove('active');
          selectionRight.classList.remove('active');
        }
      }
    }
  }

  function mouseup({target}) {
    if (currentlyDragging && triggered && target === canvas && currentPosition) {
      let start, end;
      if (startPosition < currentPosition) {
        start = moment(startPosition);
        end = moment(currentPosition);
      } else {
        start = moment(currentPosition);
        end = moment(startPosition);
      }

      updateReport(
        {
          filter: {
            $set: [
              ...filters.filter(filter => filter.type !== type),
              {
                type,
                data: {
                  type: 'fixed',
                  start: start.format('YYYY-MM-DDTHH:mm:ss'),
                  end: end.format('YYYY-MM-DDTHH:mm:ss')
                }
              }
            ]
          }
        },
        true
      );
    }

    currentlyDragging = false;
    selectionLeft.classList.remove('active');
    selectionRight.classList.remove('active');

    document.body.removeEventListener('mousemove', mousemove);
    document.body.removeEventListener('mouseup', mouseup);
  }

  return {
    afterInit: function(chart) {
      canvas = chart.canvas;

      const container = canvas.parentNode;
      container.appendChild(selectionLeft);
      container.appendChild(selectionRight);

      chart.options.onHover = onHover;
      canvas.addEventListener('mousedown', mousedown);
    },
    destroy: function() {
      const container = canvas.parentNode;
      container.removeChild(selectionLeft);
      container.removeChild(selectionRight);

      canvas.removeEventListener('mousedown', mousedown);
    }
  };
};
