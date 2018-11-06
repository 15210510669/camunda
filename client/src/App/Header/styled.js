import styled from 'styled-components';
import {Link} from 'react-router-dom';

import {Colors, themed, themeStyle} from 'modules/theme';
import ComboBadgeComponent from 'modules/components/ComboBadge';
import {ReactComponent as Logo} from 'modules/components/Icon/logo.svg';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const HEADER_HEIGHT = 56;
const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)'
});

export const LogoIcon = styled(Logo)`
  position: relative;
  top: 2px;

  width: 15px;
  height: 15px;
  margin-right: 20px;
`;

export const Dashboard = themed(styled(withStrippedProps(['isActive'])(Link))`
  display: inline-block;
  padding: 0 20px;
  border-right: 1px solid ${separator};

  ${LogoIcon} {
    ${({isActive}) => (isActive ? '' : `opacity: 0.8`)};
  }

  span {
    ${({isActive}) => (isActive ? '' : `opacity: 0.5;`)};
  }
`);

export const Header = themed(styled.header`
  height: ${HEADER_HEIGHT}px;
  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight01
  })};
  padding: 9px 0 0 0;
  font-size: 15px;
  font-weight: 500;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  line-height: 19px;
  & > span {
    display: inline-block;
  }

  /* prevents user dropdown for going under content */
  /* each page content, with display: flex; should have a smaller z-index */
  position: relative;
  z-index: 3;
`);

export const ListLink = themed(styled(withStrippedProps(['isActive'])(Link))`
  margin-left: 20px;
  display: flex;
  height: 20px;
  align-items: center;
  & span {
    ${({isActive}) => (isActive ? '' : `opacity: 0.5;`)};
  }
`);

export const SelectionBadgeLeft = themed(styled(ComboBadgeComponent.Left)`
  border-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight01
  })};
`);

export const Detail = themed(styled.span`
  padding-left: 20px;
  margin-left: 20px;
  border-left: 1px solid ${separator};
`);

export const ProfileDropdown = styled.span`
  margin-right: 20px;
  float: right;
`;

export const Menu = styled.ul`
  display: inline-block;
  margin: 0;
  padding: 0;

  list-style: none;

  li {
    display: inline-block;
  }
`;
