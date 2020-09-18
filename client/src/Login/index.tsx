/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';

import {login} from 'modules/stores/login';
import {Pages} from 'modules/constants/pages';
import {LoadingOverlay} from './LoadingOverlay';
import {
  Container,
  Input,
  FormContainer,
  CopyrightNotice,
  Logo,
  Title,
  Button,
  Error,
} from './styled';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';

interface FormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const history = useHistory();
  const {handleLogin} = login;

  return (
    <Container>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            await handleLogin(username, password);
            return history.push(Pages.Initial());
          } catch {
            return {[FORM_ERROR]: 'Username and Password do not match.'};
          }
        }}
      >
        {({handleSubmit, form, submitError}) => {
          const {submitting} = form.getState();

          return (
            <form onSubmit={handleSubmit}>
              {submitting && (
                <LoadingOverlay data-testid="login-loading-overlay" />
              )}
              <FormContainer hasError={submitError !== undefined}>
                <Logo />
                <Title>Zeebe Tasklist</Title>
                {submitError !== undefined && <Error>{submitError}</Error>}
                <Field<FormValues['username']> name="username" type="text">
                  {({input}) => (
                    <Input
                      {...input}
                      placeholder="Username"
                      id={input.name}
                      required
                    />
                  )}
                </Field>
                <Field<FormValues['password']> name="password" type="password">
                  {({input}) => (
                    <Input
                      {...input}
                      placeholder="Password"
                      id={input.name}
                      required
                    />
                  )}
                </Field>
                <Button type="submit" disabled={submitting}>
                  Login
                </Button>
              </FormContainer>
            </form>
          );
        }}
      </Form>
      <CopyrightNotice>{getCurrentCopyrightNoticeText()}</CopyrightNotice>
    </Container>
  );
};

export {Login};
