/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Header} from './styled';
import {Title} from '../PanelTitle';
import {forwardRef} from 'react';
import pluralSuffix from 'modules/utils/pluralSuffix';

type Props = {
  title: string;
  count?: number;
  children?: React.ReactNode;
  className?: string;
  hasTopBorder?: boolean;
  size?: 'sm' | 'md';
};

const PanelHeader = forwardRef<HTMLElement, Props>(
  ({title, count = 0, children, className, size = 'md'}, ref) => {
    return (
      <Header className={className} ref={ref} $size={size}>
        <Title>
          {title}
          {count > 0 && (
            <>
              &nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;
              {pluralSuffix(count, 'result')}
            </>
          )}
        </Title>
        {children}
      </Header>
    );
  },
);

export {PanelHeader};
