import React from 'react';
import classnames from 'classnames';
import ReactTable from 'react-table';
import {Button} from 'components';
import {flatten} from 'services';

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
    const {
      className,
      head,
      body,
      disableReportScrolling,
      disablePagination,
      resultType = '',
      updateSorting,
      sorting
    } = this.props;

    const columns = Table.formatColumns(resultType, head);
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
          getTheadThProps={(state, rowInfo, {sortBy}) => ({
            style: {
              cursor: updateSorting ? 'pointer' : 'default',
              boxShadow:
                sortBy === (sorting && sorting.by)
                  ? `inset 0 ${sorting.order === 'desc' ? '-' : ''}3px 0 0 rgba(0,0,0,.6)`
                  : 'none'
            },
            onClick: evt => {
              if (evt.target.className !== 'rt-resizer' && updateSorting) {
                updateSorting(
                  sortBy,
                  sorting && sorting.by === sortBy && sorting.order === 'asc' ? 'desc' : 'asc'
                );
              }
            }
          })}
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
    const resizableContainer = this.tableRef && this.tableRef.closest('.DashboardObject');
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

  static formatColumns = (resultType, head, ctx = '') => {
    return head.map((elem, i) => {
      if (typeof elem === 'string' || elem.id) {
        const accessor = convertHeaderNameToAccessor(ctx + (elem.id || elem));
        let sortBy = accessor;
        if (resultType.includes('Map')) {
          sortBy = i === 0 ? 'key' : 'value';
        }
        return {
          Header: elem.label || elem,
          accessor,
          minWidth: 100,
          sortBy
        };
      }
      return {
        Header: elem.label,
        columns: Table.formatColumns(resultType, elem.columns, ctx + elem.label)
      };
    });
  };

  static formatData = (head, body) => {
    const flatHead = head.reduce(flatten('', entry => entry.id || entry), []);
    return body.map(row => {
      const newRow = {};
      row.forEach((cell, columnIdx) => {
        newRow[convertHeaderNameToAccessor(flatHead[columnIdx])] = cell;
      });
      return newRow;
    });
  };
}

function convertHeaderNameToAccessor(name) {
  const joined = name
    .split(' ')
    .join('')
    .replace('Variables', 'Variable:');

  return joined.charAt(0).toLowerCase() + joined.slice(1);
}
