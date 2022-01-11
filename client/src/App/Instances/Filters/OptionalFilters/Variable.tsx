/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import {VariableHeader} from './styled';
import {observer} from 'mobx-react';
import {TextField} from 'modules/components/TextField';
import {
  validateVariableNameComplete,
  validateVariableValueComplete,
} from '../validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {OptionalFilter} from './OptionalFilter';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  return (
    <OptionalFilter
      name="variable"
      filterList={['variableName', 'variableValue']}
    >
      <VariableHeader appearance="emphasis">Variable</VariableHeader>
      <Field name="variableName" validate={validateVariableNameComplete}>
        {({input, meta}) => (
          <TextField
            {...input}
            type="text"
            data-testid="filter-variable-name"
            label="Name"
            shouldDebounceError={!meta.dirty && formState.dirty}
          />
        )}
      </Field>
      <Field name="variableValue" validate={validateVariableValueComplete}>
        {({input, meta}) => (
          <TextField
            {...input}
            type="text"
            placeholder="in JSON format"
            data-testid="filter-variable-value"
            label="Value"
            fieldSuffix={{
              type: 'icon',
              icon: 'window',
              press: () => {
                setIsModalVisible(true);
              },
              tooltip: 'Open JSON editor modal',
            }}
            shouldDebounceError={!meta.dirty && formState.dirty}
          />
        )}
      </Field>
      <JSONEditorModal
        title={`Edit Variable Value`}
        value={formState.values?.variableValue}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onSave={(value) => {
          form.change('variableValue', value);
          setIsModalVisible(false);
        }}
        isModalVisible={isModalVisible}
      />
    </OptionalFilter>
  );
});

export {Variable};
