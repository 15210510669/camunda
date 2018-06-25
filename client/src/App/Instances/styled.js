import styled from 'styled-components';

import SplitPane from 'modules/components/SplitPane';
import Panel from 'modules/components/Panel';

import {HEADER_HEIGHT} from './../Header/styled';

export const Filter = styled.div`
  display: flex;
  flex-direction: row;
  height: calc(100vh - ${HEADER_HEIGHT}px);

  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;

export const Left = styled.div`
  display: flex;
  width: 320px;
  margin-right: 1px;
`;

export const Center = styled(SplitPane)`
  width: 100%;
`;

export const Right = styled.div`
  width: 320px;
  display: flex;
`;

export const SelectionHeader = styled(Panel.Header)`
  padding-left: 45px;
`;
