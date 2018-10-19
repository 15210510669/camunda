import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import BasicPanel from '../Panel';

export const COLLAPSABLE_PANEL_MIN_WIDTH = '56px';

export const Collapsable = themed(styled.div`
  position: relative;

  overflow: hidden;
  width: ${({isCollapsed, maxWidth}) =>
    isCollapsed ? COLLAPSABLE_PANEL_MIN_WIDTH : `${maxWidth}px`};
  height: 100%;

  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};

  border-radius: 0 3px 0 0;

  transition: width 0.2s ease-out;
`);

const panelStyle = css`
position: absolute;
top: 0;
left: 0;
height: 100%;
width 100%;
transition: visibility 0.2s ease-out, opacity 0.2s ease-out;
`;

export const ExpandedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
  visibility: ${({isCollapsed}) => (isCollapsed ? 'hidden' : 'visible')};
  z-index: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
  border-radius: 3px 3px 0 0;
`;

export const CollapsedPanel = styled(BasicPanel)`
  ${panelStyle}
  opacity: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
  visibility: ${({isCollapsed}) => (isCollapsed ? 'visible' : 'hidden')};
  z-index: ${({isCollapsed}) => (isCollapsed ? '1' : '0')};
`;

export const Header = styled(BasicPanel.Header)`
  border-radius: 3px 3px 0 0;
`;
