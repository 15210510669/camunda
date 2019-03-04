/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {createPortal} from 'react-dom';

import Button from 'modules/components/Button';

import * as Styled from './styled';

const ModalContext = React.createContext({});

function withModal(Component) {
  function WithModal(props) {
    return (
      <ModalContext.Consumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </ModalContext.Consumer>
    );
  }

  WithModal.WrappedComponent = Component;

  WithModal.displayName = `WithModal(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithModal;
}

export default class Modal extends React.Component {
  static propTypes = {
    onModalClose: PropTypes.func.isRequired,
    children: PropTypes.node,
    className: PropTypes.string
  };

  constructor(props) {
    super(props);
    this.keyHandlers = new Map([
      [27, this.props.onModalClose],
      [9, this.handleTabKeyDown]
    ]);
    this.modalRef = React.createRef();
    this.prevActiveElement = document.activeElement;
  }

  componentDidMount() {
    document.activeElement && document.activeElement.blur();
    document.addEventListener('keydown', this.handleKeyDown);
  }

  componentWillUnmount() {
    document.removeEventListener('keydown', this.handleKeyDown);
    this.prevActiveElement.focus();
  }

  handleKeyDown = e => {
    const keyHandler = this.keyHandlers.get(e.keyCode);
    return keyHandler && keyHandler(e);
  };

  handleTabKeyDown = e => {
    const focusableModalElements = this.modalRef.current.querySelectorAll(
      'a[href], button, textarea, input[type="text"], input[type="radio"], input[type="checkbox"], select'
    );
    const firstElement = focusableModalElements[0];
    const lastElement =
      focusableModalElements[focusableModalElements.length - 1];

    if (!e.shiftKey && document.activeElement !== firstElement) {
      firstElement.focus();
      return e.preventDefault();
    }

    if (e.shiftKey && document.activeElement !== lastElement) {
      lastElement.focus();
      e.preventDefault();
    }
  };

  addKeyHandler = (keyCode, handler) => this.keyHandlers.set(keyCode, handler);

  render() {
    const {onModalClose, children, className} = this.props;
    return createPortal(
      <Styled.ModalRoot className={className} ref={this.modalRef} role="dialog">
        <Styled.ModalContent>
          <ModalContext.Provider
            value={{
              onModalClose,
              addKeyHandler: this.addKeyHandler
            }}
          >
            {children}
          </ModalContext.Provider>
        </Styled.ModalContent>
      </Styled.ModalRoot>,
      document.body
    );
  }
}

Modal.Header = function ModalHeader({children, ...props}) {
  return (
    <Styled.ModalHeader {...props}>
      {children}
      <ModalContext.Consumer>
        {modalContext => (
          <Styled.CrossButton
            data-test="cross-button"
            onClick={modalContext.onModalClose}
            title="Close Modal"
          >
            <Styled.CrossIcon />
          </Styled.CrossButton>
        )}
      </ModalContext.Consumer>
    </Styled.ModalHeader>
  );
};

Modal.Header.propTypes = {
  children: PropTypes.node
};

Modal.Body = Styled.ModalBody;
Modal.BodyText = Styled.ModalBodyText;

Modal.Footer = function ModalFooter(props) {
  return (
    <Styled.ModalFooter className={props.className}>
      {props.children}
    </Styled.ModalFooter>
  );
};

Modal.Footer.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node
};

class ModalPrimaryButton extends React.Component {
  static propTypes = {
    addKeyHandler: PropTypes.func.isRequired,
    className: PropTypes.string,
    children: PropTypes.node
  };

  componentDidMount() {
    this.props.addKeyHandler(13, this.handleReturnKeyPress);
  }

  primaryButtonRef = React.createRef();

  handleReturnKeyPress = e => {
    e.preventDefault();
    this.primaryButtonRef.current.click();
  };

  render() {
    const {onModalClose, ...props} = this.props;
    return (
      <Button
        ref={this.primaryButtonRef}
        color="primary"
        size="medium"
        {...props}
      />
    );
  }
}

Modal.PrimaryButton = withModal(ModalPrimaryButton);
