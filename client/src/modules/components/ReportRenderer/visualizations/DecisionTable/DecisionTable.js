import React from 'react';

import './DecisionTable.scss';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';

import {themed} from 'theme';

import Viewer from 'dmn-js';
import createHitsColumnAddon from './HitsColumnAddon';
import DmnJsPortal from './DmnJsPortal';

export default themed(
  class DecisionTable extends React.Component {
    state = {
      entryPoints: {rules: {}}
    };

    container = React.createRef();

    componentDidMount() {
      const {
        configuration: {xml},
        decisionDefinitionKey
      } = this.props.report.data;

      this.loadXML(xml, decisionDefinitionKey);
    }

    componentDidUpdate({
      report: {
        data: {
          decisionDefinitionKey: prevKey,
          configuration: {xml: prevXml}
        }
      }
    }) {
      const {
        configuration: {xml},
        decisionDefinitionKey
      } = this.props.report.data;

      if (prevXml !== xml || prevKey !== decisionDefinitionKey) {
        this.loadXML(xml, decisionDefinitionKey);
      }
    }

    loadXML = (xml, decisionDefinitionKey) => {
      const {entryPoints, Addon: HitsColumn} = createHitsColumnAddon();

      if (this.viewer) {
        this.viewer.destroy();
      }

      this.viewer = new Viewer({
        container: this.container.current,
        decisionTable: {additionalModules: [HitsColumn]}
      });

      this.viewer.importXML(xml, () =>
        this.viewer.open(
          this.viewer
            .getViews()
            .find(
              ({type, element: {id}}) => type === 'decisionTable' && id === decisionDefinitionKey
            ),
          () => this.setState({entryPoints})
        )
      );
    };

    renderRuleCell = ruleId => {
      const {
        result,
        decisionInstanceCount,
        data: {
          configuration: {hideAbsoluteValue, hideRelativeValue, showGradientBars}
        }
      } = this.props.report;

      const resultNumber = result[ruleId] || 0;
      const percentage = Math.round((resultNumber / decisionInstanceCount) * 1000) / 10 || 0;

      const node = this.state.entryPoints.rules[ruleId];
      if (showGradientBars) {
        const progress = resultNumber / decisionInstanceCount;

        node.style.background = `linear-gradient(to right, ${getColor(
          this.props.theme,
          0
        )} 0%, ${getColor(this.props.theme, progress)} ${percentage}%, transparent ${percentage}%)`;
      } else {
        node.style.background = '';
      }

      let outputString = `${resultNumber} (${percentage}%)`;
      if (hideAbsoluteValue && hideRelativeValue) {
        outputString = '';
      } else if (hideAbsoluteValue) {
        outputString = percentage + '%';
      } else if (hideRelativeValue) {
        outputString = resultNumber;
      }

      return (
        <DmnJsPortal key={ruleId} renderIn={this.state.entryPoints.rules[ruleId]}>
          <i>{outputString}</i>
        </DmnJsPortal>
      );
    };

    render() {
      const {rules, summary} = this.state.entryPoints;
      const {decisionInstanceCount, result} = this.props.report;

      const hitCount = Object.values(result).reduce((sum, current) => sum + current, 0);

      return (
        <div ref={this.container} className="DecisionTable">
          {Object.keys(rules).map(this.renderRuleCell)}
          <DmnJsPortal renderIn={summary}>
            <b>
              {decisionInstanceCount} Evaluation{decisionInstanceCount !== 1 ? 's' : ''}
              {hitCount > decisionInstanceCount && ` / ${hitCount} Hits`}
            </b>
          </DmnJsPortal>
        </div>
      );
    }
  }
);

function getColor(theme, progress) {
  if (theme === 'light') {
    return `hsl(223, 100%, ${90 - progress * 30}%)`;
  } else {
    return `hsl(223, 100%, ${30 + progress * 30}%)`;
  }
}
