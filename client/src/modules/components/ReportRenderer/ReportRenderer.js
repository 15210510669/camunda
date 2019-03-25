import React from 'react';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';
import SetupNotice from './SetupNotice';
import IncompleteReport from './IncompleteReport';

import {ErrorBoundary, Message} from 'components';

import {formatters} from 'services';

import {isEmpty} from './service';

import './ReportRenderer.scss';

const errorMessage =
  'Cannot display data for the given report settings. Please choose another combination!';

export default function ReportRenderer(props) {
  const {report, updateReport} = props;
  let View;
  if (report) {
    const isDecision = report.reportType === 'decision';

    if (report.combined) View = CombinedReportRenderer;
    else if (isDecision) View = DecisionReportRenderer;
    else View = ProcessReportRenderer;

    const somethingMissing = checkReport(report);
    if (somethingMissing) {
      if (updateReport) {
        return <SetupNotice>{somethingMissing}</SetupNotice>;
      } else {
        return <IncompleteReport id={report.id} />;
      }
    }

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          <View {...props} errorMessage={errorMessage} />
          {report.data.configuration.showInstanceCount && (
            <div className="additionalInfo">
              Total {isDecision ? 'Evaluation' : 'Instance'}
              <br />
              Count:
              <b>
                {formatters.frequency(
                  report.processInstanceCount || report.decisionInstanceCount || 0
                )}
              </b>
            </div>
          )}
        </div>
      </ErrorBoundary>
    );
  } else {
    return <Message type="error">{errorMessage}</Message>;
  }
}

function checkReport({data, reportType, combined}) {
  if (combined) {
    return;
  }
  if (
    reportType === 'process' &&
    (isEmpty(data.processDefinitionKey) || isEmpty(data.processDefinitionVersion))
  ) {
    return (
      <p>
        Select a <b>Process Definition</b> above.
      </p>
    );
  } else if (
    reportType === 'decision' &&
    (isEmpty(data.decisionDefinitionKey) || isEmpty(data.decisionDefinitionVersion))
  ) {
    return (
      <p>
        Select a <b>Decision Definition</b> above.
      </p>
    );
  } else if (!data.view) {
    return (
      <p>
        Select an option for <b>View</b> above.
      </p>
    );
  } else if (!data.groupBy) {
    return (
      <p>
        Select what to <b>Group by</b> above.
      </p>
    );
  } else if (!data.visualization) {
    return (
      <p>
        Select an option for <b>Visualize as</b> above.
      </p>
    );
  } else {
    return;
  }
}
