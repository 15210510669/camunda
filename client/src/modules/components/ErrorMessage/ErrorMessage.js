import React from 'react';

import classnames from 'classnames';

import './ErrorMessage.scss';

export default function ErrorMessage(props) {
  return <div className={classnames('ErrorMessage', props.className)}>{props.children}</div>;
}
