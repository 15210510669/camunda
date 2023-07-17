/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, CloseIcon, CheckIcon} from './styled';
import {useForm, useFormState} from 'react-final-form';
import {getError} from '../getError';
import {useFieldError} from 'modules/hooks/useFieldError';
import {ActionButton} from 'modules/components/ActionButton';

type Props = {
  className?: string;
  onExitEditMode?: () => void;
};

const EditButtons: React.FC<Props> = ({className, onExitEditMode}) => {
  const form = useForm();
  const {values, initialValues, validating, hasValidationErrors} =
    useFormState();

  const nameError = useFieldError('name');
  const valueError = useFieldError('value');
  const errorMessage = getError(
    initialValues.name === '' ? nameError : undefined,
    valueError,
  );

  return (
    <Container className={className}>
      <ActionButton
        title="Exit edit mode"
        onClick={() => {
          onExitEditMode?.();
          form.reset({});
        }}
        icon={<CloseIcon />}
      />
      <ActionButton
        title="Save variable"
        disabled={
          initialValues.value === values.value ||
          validating ||
          hasValidationErrors ||
          errorMessage !== undefined
        }
        onClick={() => form.submit()}
        icon={<CheckIcon />}
      />
    </Container>
  );
};

export {EditButtons};
