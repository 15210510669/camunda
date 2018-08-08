import React from 'react';
import {mount} from 'enzyme';

import Modal from './Modal';

it('should not render anything if the modal is not opened', () => {
  const node = mount(<Modal>ModalContent</Modal>);

  expect(node.html()).toBe(null);
});

it('should render basic children', () => {
  const node = mount(<Modal open={true}>ModalContent</Modal>);

  expect(node).toIncludeText('ModalContent');
});

it('should call the onClose function on backdrop click', () => {
  const spy = jest.fn();
  const node = mount(
    <Modal open={true} onClose={spy}>
      ModalContent
    </Modal>
  );

  node.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should not call the onClose function when modal content is clicked', () => {
  const spy = jest.fn();
  const node = mount(
    <Modal open={true} onClose={spy}>
      <button>Some button in the modal</button>
    </Modal>
  );

  node.find('button').simulate('click');

  expect(spy).not.toHaveBeenCalled();
});

it('should call the onConfirm function when enter is pressed', () => {
  const spy = jest.fn();
  mount(
    <Modal open={true} onConfirm={spy}>
      <input />
    </Modal>
  );

  const event = new KeyboardEvent('keydown', {key: 'Enter'});
  window.dispatchEvent(event);

  expect(spy).toHaveBeenCalledWith(event);
});

it('should call the onConfirm function when enter is pressed', () => {
  const spy = jest.fn();
  mount(<Modal open={false} onConfirm={spy} />);

  const event = new KeyboardEvent('keydown', {key: 'Enter'});
  window.dispatchEvent(event);

  expect(spy).not.toHaveBeenCalled();
});

describe('Header', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open={true}>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });

  it('should contain a close button', () => {
    const node = mount(
      <Modal open={true}>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    expect(node.find('button').length).toBe(1);
  });

  it('should call the onClose function on close button click', () => {
    const spy = jest.fn();
    const node = mount(
      <Modal open={true} onClose={spy}>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    node.find('button').simulate('click');
    expect(spy).toHaveBeenCalled();
  });
});

describe('Content', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open={true}>
        <Modal.Content>
          <div className="test">test</div>
        </Modal.Content>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });
});

describe('Actions', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open={true}>
        <Modal.Actions>
          <div className="test">test</div>
        </Modal.Actions>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });
});
