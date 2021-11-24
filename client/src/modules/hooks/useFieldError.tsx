/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useField, useFormState} from 'react-final-form';
import {useEffect, useState} from 'react';

const useFieldError = (name: string) => {
  const {
    input: {value},
    meta: {active, dirtySinceLastSubmit, validating},
  } = useField(name);

  const {errors, submitErrors} = useFormState();
  const error = errors?.[name];
  const submitError = dirtySinceLastSubmit ? undefined : submitErrors?.[name];

  const [computedError, setComputedError] = useState<string | undefined>();

  useEffect(() => {
    if (validating && !active) {
      return;
    }

    setComputedError(error ?? submitError);
  }, [validating, active, error, submitError, setComputedError, value]);

  return computedError;
};
export {useFieldError};
