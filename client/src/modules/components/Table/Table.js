import React from 'react';
import classnames from 'classnames';
import ReactTable from 'react-table';
import {Button} from 'components';

import './Table.scss';

const defaultPageSize = 20;

export default class Table extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      resizedState: []
    };
  }

  render() {
    const {className, head, body, disableReportScrolling, disablePagination} = this.props;

    const columns = Table.formatColumns(head);
    const data = Table.formatData(head, body);

    // react-table does not support Infinity as page size 👎
    const pageSize = disablePagination ? Number.MAX_VALUE : defaultPageSize;

    return (
      <div className={classnames('Table__container', className)} ref={ref => (this.tableRef = ref)}>
        <ReactTable
          data={data}
          columns={columns}
          resized={this.state.resizedState}
          pageSize={pageSize}
          showPagination={data.length > pageSize}
          showPaginationTop={false}
          showPaginationBottom={true}
          showPageSizeOptions={false}
          minRows={0}
          sortable={false}
          multiSort={false}
          className={classnames('-striped', '-highlight', 'Table', {
            'Table__unscrollable-mode': disableReportScrolling
          })}
          noDataText="No data available"
          onResizedChange={this.updateResizedState}
          PreviousComponent={props => <Button {...props} />}
          NextComponent={props => <Button {...props} />}
        />
      </div>
    );
  }

  updateResizedState = columns => {
    this.setState({
      resizedState: columns.map(column => {
        return {
          ...column,
          value: Math.max(column.value, 40)
        };
      })
    });
  };

  fixColumnAlignment = () => {
    if (this.tableRef) {
      const {clientWidth, offsetWidth} = this.tableRef.querySelector('.rt-tbody');
      const margin = clientWidth < offsetWidth ? offsetWidth - clientWidth : 0;

      this.tableRef.querySelectorAll('.rt-thead > .rt-tr').forEach(({style}) => {
        style.marginRight = margin + 'px';
      });
    }
  };

  componentDidMount() {
    this.fixColumnAlignment();

    // on dashboards
    const resizableContainer = this.tableRef.closest('.DashboardObject');
    if (resizableContainer) {
      new MutationObserver(this.fixColumnAlignment).observe(resizableContainer, {
        attributes: true
      });
    }

    // on report page
    window.addEventListener('resize', this.fixColumnAlignment);

    // for dynamic content (e.g. targetValue modal)
    new MutationObserver(this.fixColumnAlignment).observe(this.tableRef, {
      childList: true,
      subtree: true
    });
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.fixColumnAlignment);
  }

  static formatColumns = (head, ctx = '') => {
    return head.map(elem => {
      if (typeof elem === 'string') {
        return {
          Header: elem,
          accessor: convertHeaderNameToAccessor(ctx + elem),
          minWidth: 100
        };
      }
      return {
        Header: elem.label,
        columns: Table.formatColumns(elem.columns, ctx + elem.label)
      };
    });
  };

  static formatData = (head, body) => {
    const flatHead = head.reduce(flatten(), []);
    return body.map(row => {
      const newRow = {};
      row.forEach((cell, columnIdx) => {
        newRow[convertHeaderNameToAccessor(flatHead[columnIdx])] = cell;
      });
      return newRow;
    });
  };
}

const flatten = (ctx = '') => (flat, entry) => {
  if (entry.columns) {
    // nested column, flatten recursivly with augmented context (e.g. for providing a Variable prefix)
    return flat.concat(entry.columns.reduce(flatten(ctx + entry.label), []));
  } else {
    // normal column, return column name prefixed with current context
    return flat.concat(ctx + entry);
  }
};

function convertHeaderNameToAccessor(name) {
  return name
    .split(' ')
    .join('_')
    .toLowerCase();
}
