import React from 'react';
import {shallow} from 'enzyme';
import {Redirect} from 'react-router-dom';

import {
  flushPromises,
  mockResolvedAsyncFn,
  mockRejectedAsyncFn
} from 'modules/testUtils';

import Login from './Login';
import * as Styled from './styled';
import * as api from 'modules/api/login';
import {REQUIRED_FIELD_ERROR, LOGIN_ERROR} from './constants';

jest.mock('modules/request');
jest.mock('modules/api/login');

describe('Login', () => {
  let node;
  let usernameInput;
  let passwordInput;

  beforeEach(() => {
    node = shallow(<Login location={{}} />)
      .first()
      .shallow();

    usernameInput = node.findWhere(
      element =>
        element.prop('type') === 'text' && element.prop('name') === 'username'
    );

    passwordInput = node.findWhere(
      element =>
        element.prop('type') === 'password' &&
        element.prop('name') === 'password'
    );
  });

  it('should render initially with no state data', () => {
    expect(node.state('username')).toEqual('');
    expect(node.state('password')).toEqual('');
    expect(node.state('forceRedirect')).toBe(false);
    expect(node.state('error')).toBeNull();
  });

  it('should render login form by default', () => {
    // given
    const submitInput = node.findWhere(
      element => element.prop('type') === 'submit'
    );

    // then
    expect(node.find(Styled.Login)).toHaveLength(1);
    expect(usernameInput).toHaveLength(1);
    expect(passwordInput).toHaveLength(1);
    expect(submitInput).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should change state according to inputs change', () => {
    //given
    const username = 'foo';
    const password = 'bar';

    // when
    usernameInput.simulate('change', {
      target: {name: 'username', value: username}
    });

    passwordInput.simulate('change', {
      target: {name: 'password', value: password}
    });

    // then
    expect(node.state('username')).toEqual(username);
    expect(node.state('password')).toEqual(password);
  });

  it('should reset the stored state on login', async () => {
    // given
    const spy = jest.fn();
    node.setProps({clearStateLocally: spy});
    node.setState({username: 'foo', password: 'bar'});

    // when
    node.instance().handleLogin({preventDefault: () => {}});
    await flushPromises();

    // then
    expect(spy).toHaveBeenCalled();
  });

  describe('redirection', () => {
    let originalLogin = api.login;
    let username = 'foo',
      password = 'bar';
    api.login = mockResolvedAsyncFn();

    beforeEach(() => {
      api.login.mockClear();
      node.setState({username, password});
    });

    afterAll(() => {
      // reset api.login
      api.login = originalLogin.bind(api);
    });

    it('should redirect to home page on successful login', async () => {
      // when
      node.instance().handleLogin({preventDefault: () => {}});
      await flushPromises();
      node.update();

      // then
      expect(api.login).toBeCalledWith({username, password});
      const RedirectNode = node.find(Redirect);
      expect(RedirectNode).toHaveLength(1);
      expect(RedirectNode.prop('to')).toBe('/');
      expect(node).toMatchSnapshot();
    });

    it('should redirect to referrer page on successful login', async () => {
      // given
      const referrer = '/some/page';
      node.setProps({location: {state: {referrer}}});

      // when
      node.instance().handleLogin({preventDefault: () => {}});
      await flushPromises();
      node.update();

      // then
      expect(api.login).toBeCalledWith({username, password});
      const RedirectNode = node.find(Redirect);
      expect(RedirectNode).toHaveLength(1);
      expect(RedirectNode.prop('to')).toBe(referrer);
      expect(node).toMatchSnapshot();
    });
  });

  it('should display an error if any field is empty', async () => {
    // mock api.login
    const originalLogin = api.login;
    api.login = jest.fn();

    // when
    node.instance().handleLogin({preventDefault: () => {}});
    await flushPromises();
    node.update();

    // then
    expect(api.login).not.toHaveBeenCalled();
    const errorSpan = node.find(Styled.FormError).render();
    expect(node.state('error')).toEqual(REQUIRED_FIELD_ERROR);
    expect(errorSpan.text()).toContain(REQUIRED_FIELD_ERROR);
    expect(node).toMatchSnapshot();

    // reset api.login
    api.login = originalLogin.bind(api);
  });

  it('should display an error on unsuccessful login', async () => {
    // mock api.login
    const originalLogin = api.login;
    api.login = mockRejectedAsyncFn();
    node.setState({username: 'foo', password: 'bar'});

    // when
    node.instance().handleLogin({preventDefault: () => {}});
    await flushPromises();
    node.update();

    // then
    const errorSpan = node.find(Styled.FormError).render();
    expect(node.state('error')).toEqual(LOGIN_ERROR);
    expect(errorSpan.text()).toContain(LOGIN_ERROR);
    expect(node).toMatchSnapshot();

    // reset api.login
    api.login = originalLogin.bind(api);
  });
});
