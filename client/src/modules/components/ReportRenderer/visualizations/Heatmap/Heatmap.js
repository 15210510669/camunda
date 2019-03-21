import React from 'react';

import {BPMNDiagram, TargetValueBadge, LoadingIndicator} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import {calculateTargetValueHeat} from './service';
import {formatters, getTooltipText} from 'services';

import './Heatmap.scss';

const Heatmap = ({report, formatter, errorMessage}) => {
  const {
    result,
    data: {
      view: {property},
      configuration: {alwaysShowAbsolute, alwaysShowRelative, heatmapTargetValue: targetValue, xml}
    },
    processInstanceCount
  } = report;

  if (!result || typeof result !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if (!xml) {
    return <LoadingIndicator />;
  }

  let heatmapComponent;
  if (targetValue && targetValue.active && !targetValue.values.target) {
    const heat = calculateTargetValueHeat(result, targetValue.values);
    heatmapComponent = [
      <HeatmapOverlay
        key="heatmap"
        data={heat}
        alwaysShowAbsolute={alwaysShowAbsolute}
        alwaysShowRelative={alwaysShowRelative}
        formatter={(_, id) => {
          const node = document.createElement('div');

          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = result[id];

          node.innerHTML = `target duration: ${formatters.duration(target)}<br/>`;

          if (typeof real === 'number') {
            const relation = (real / target) * 100;

            node.innerHTML += `actual duration: ${formatters.duration(real)}<br/>${
              relation < 1 ? '< 1' : Math.round(relation)
            }% of the target value`;
          } else {
            node.innerHTML += `No actual value available.<br/>Cannot compare target and actual value.`;
          }

          // tooltips don't work well with spaces
          node.innerHTML = node.innerHTML.replace(/ /g, '\u00A0');

          return node;
        }}
      />,
      <TargetValueBadge key="targetValueBadge" values={targetValue.values} />
    ];
  } else {
    heatmapComponent = (
      <HeatmapOverlay
        data={result}
        alwaysShowAbsolute={alwaysShowAbsolute}
        alwaysShowRelative={alwaysShowRelative}
        formatter={data =>
          getTooltipText(
            data,
            formatter,
            processInstanceCount,
            alwaysShowAbsolute,
            alwaysShowRelative,
            property
          )
        }
      />
    );
  }

  return (
    <div className="Heatmap">
      <BPMNDiagram xml={xml}>{heatmapComponent}</BPMNDiagram>
    </div>
  );
};

export default Heatmap;
