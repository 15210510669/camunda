/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import Table from 'modules/components/Table';
import {Container, THead, TH, TR, TD} from './styled';

type Column = {
  id: string;
  dataTestId?: string;
  cellContent: string | React.ReactNode;
  isBold?: boolean;
  width?: string;
};

type Row = {
  id: string;
  columns: Column[];
};

type Props = {
  headerColumns: Omit<Column, 'id'>[];
  rows: Row[];
  hasFixedColumnWidths?: boolean;
  className?: string;
};

const DataTable = React.forwardRef<HTMLDivElement, Props>(
  ({headerColumns, rows, hasFixedColumnWidths, className}, ref) => {
    return (
      <Container className={className} ref={ref}>
        <Table>
          <THead>
            <TR $hideLastChildBottomBorder>
              {headerColumns.map(({cellContent, isBold, width}, index) => (
                <TH key={index} $isBold={isBold} $width={width}>
                  {cellContent}
                </TH>
              ))}
            </TR>
          </THead>
          <tbody>
            {rows.map(({id, columns}) => {
              return (
                <TR key={id}>
                  {columns.map(({id, dataTestId, cellContent, isBold}) => (
                    <TD
                      data-testid={dataTestId}
                      key={id}
                      $isBold={isBold}
                      $hasFixedColumnWidths={hasFixedColumnWidths}
                    >
                      {cellContent}
                    </TD>
                  ))}
                </TR>
              );
            })}
          </tbody>
        </Table>
      </Container>
    );
  },
);

export {DataTable};
