/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Table, Button, Icon, NoDataNotice, LoadingIndicator} from 'components';
import {loadCommonOutliersVariables, getInstancesDownloadUrl} from './service';
import {t} from 'translation';
import './VariablesTable.scss';

export default class VariablesTable extends React.Component {
  state = {
    data: null,
  };

  async componentDidMount() {
    const {id, higherOutlier} = this.props.selectedNode;
    const data = await loadCommonOutliersVariables({
      ...this.props.config,
      flowNodeId: id,
      higherOutlierBound: higherOutlier.boundValue,
    });

    this.setState({data});
  }

  constructTableBody = (data) => {
    const {id, higherOutlier} = this.props.selectedNode;

    return data.map((row) => [
      <div className="outliersCount">
        {row.instanceCount}{' '}
        {t(`analysis.outlier.tooltip.instance.label${row.instanceCount !== 1 ? '-plural' : ''}`)}
        <a
          href={getInstancesDownloadUrl({
            ...this.props.config,
            flowNodeId: id,
            higherOutlierBound: higherOutlier.boundValue,
            variableName: row.variableName,
            variableTerm: row.variableTerm,
            fileName: `${row.variableName}_Outliers.csv`,
          })}
        >
          <Button>
            <Icon type="save" />
            Instance ID's CSV
          </Button>
        </a>
      </div>,
      +(row.outlierToAllInstancesRatio * 100).toFixed(2),
      +(row.outlierRatio * 100).toFixed(2),
      row.variableName + '=' + row.variableTerm,
    ]);
  };

  render() {
    const {data} = this.state;
    let tableData;
    if (data?.length) {
      tableData = {
        head: [
          t('analysis.outlier.detailsModal.table.outliersNumber'),
          t('analysis.outlier.detailsModal.table.ofTotalPercentage'),
          t('analysis.outlier.detailsModal.table.ofOutliersPercentage'),
          t('report.variables.default'),
        ],
        body: this.constructTableBody(data),
      };
    } else {
      tableData = {
        head: [],
        body: [],
        noData: data ? (
          <NoDataNotice>{t('analysis.outlier.detailsModal.table.emptyTableMessage')}</NoDataNotice>
        ) : (
          <LoadingIndicator />
        ),
      };
    }

    return (
      <div className="VariablesTable">
        <Table {...tableData} foot={[]} disablePagination />
      </div>
    );
  }
}
