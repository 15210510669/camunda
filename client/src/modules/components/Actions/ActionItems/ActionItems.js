import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: Styled.RetryIcon,
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: Styled.CancelIcon
};

export default function ActionItems(props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

ActionItems.propTypes = {
  children: PropTypes.node.isRequired
};

ActionItems.Item = function Item({title, onClick, type, ...rest}) {
  const Icon = iconsMap[type];

  return (
    <Styled.Li>
      <Styled.Button {...rest} type={type} title={title} onClick={onClick}>
        <Icon />
      </Styled.Button>
    </Styled.Li>
  );
};

ActionItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(OPERATION_TYPE)).isRequired,
  onClick: PropTypes.func,
  title: PropTypes.string
};
