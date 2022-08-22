/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
import {
  Container,
  NameField,
  ValueField,
  InputFieldContainer,
  EditButtons,
} from './styled';
import {
  validateNameCharacters,
  validateNameComplete,
  validateNameNotDuplicate,
  validateValueValid,
  validateValueComplete,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';

const NewVariable: React.FC = () => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);

  return (
    <Container data-testid="add-variable-row">
      <Field
        name="name"
        validate={mergeValidators(
          validateNameCharacters,
          validateNameComplete,
          validateNameNotDuplicate
        )}
        allowNull={false}
        parse={(value) => value}
      >
        {({input, meta}) => (
          <NameField
            {...input}
            type="text"
            placeholder="Name"
            data-testid="add-variable-name"
            shouldDebounceError={!meta.dirty && formState.dirty}
            autoFocus={true}
          />
        )}
      </Field>
      <InputFieldContainer>
        <Field
          name="value"
          validate={mergeValidators(validateValueComplete, validateValueValid)}
          parse={(value) => value}
        >
          {({input, meta}) => (
            <ValueField
              {...input}
              type="text"
              placeholder="Value"
              data-testid="add-variable-value"
              fieldSuffix={{
                type: 'icon',
                icon: 'window',
                press: () => {
                  setIsModalVisible(true);
                  tracking.track({
                    eventName: 'json-editor-opened',
                    variant: 'add-variable',
                  });
                },
                tooltip: 'Open JSON editor modal',
              }}
              shouldDebounceError={!meta.dirty && formState.dirty}
            />
          )}
        </Field>
        <EditButtons />
      </InputFieldContainer>

      <JSONEditorModal
        isVisible={isModalVisible}
        title={
          formState.values?.name
            ? `Edit Variable "${formState.values?.name}"`
            : 'Edit a new Variable'
        }
        value={formState.values?.value}
        onClose={() => {
          setIsModalVisible(false);
          tracking.track({
            eventName: 'json-editor-closed',
            variant: 'add-variable',
          });
        }}
        onApply={(value) => {
          form.change('value', value);
          setIsModalVisible(false);
          tracking.track({
            eventName: 'json-editor-saved',
            variant: 'add-variable',
          });
        }}
      />
    </Container>
  );
};

export {NewVariable};
