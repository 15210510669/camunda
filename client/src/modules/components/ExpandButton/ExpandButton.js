import React from 'react';
import PropTypes from 'prop-types';

import {EXPAND_CONTAINER} from 'modules/utils';

import * as Styled from './styled';

const {TOP, BOTTOM, LEFT, RIGHT} = EXPAND_CONTAINER;

// true: expanded, false: not expanded
const iconsMap = {
  [TOP]: {true: Styled.Up, false: Styled.Down},
  [BOTTOM]: {true: Styled.Down, false: Styled.Up},
  [LEFT]: {true: Styled.Left},
  [RIGHT]: {true: Styled.Right}
};

class ExpandButton extends React.Component {
  static propTypes = {
    containerId: PropTypes.oneOf([TOP, BOTTOM, LEFT, RIGHT]).isRequired,
    isExpanded: PropTypes.bool.isRequired,
    onClick: PropTypes.func
  };

  render() {
    const {containerId, isExpanded} = this.props;
    const TargetIcon = iconsMap[containerId][isExpanded];

    return (
      <Styled.ExpandButton {...this.props} onClick={this.props.onClick}>
        <TargetIcon />
      </Styled.ExpandButton>
    );
  }
}

export default ExpandButton;
