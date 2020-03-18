/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState} from 'react';
import PropTypes from 'prop-types';

import {isValidJSON} from 'modules/utils';

import * as Styled from './styled';

export default function Variables({
  variables,
  isRunning,
  editMode,
  onVariableUpdate,
  isEditable,
  setEditMode,
  Placeholder,
  Overlay,
  isLoading
}) {
  const MODE = {EDIT: 'edit', ADD: 'add'};

  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const variablesContentRef = useRef(null);
  const editInputTDRef = useRef(null);

  /**
   * Determine, if bottom of currently opened edit textarea is
   * out of bottom bounds of visible scroll area.
   *
   * @return boolean
   */
  function isTextareaOutOfBounds() {
    const inputTD = editInputTDRef.current;
    let container = variablesContentRef.current;

    const theadHeight = 45;

    if (inputTD && container) {
      container = container.children[0];

      // distance from top edge of container to bottom edge of td
      const tdPosition =
        inputTD.offsetTop -
        theadHeight -
        container.scrollTop +
        inputTD.offsetHeight;

      return tdPosition > container.offsetHeight;
    }
  }

  function handleHeightChange() {
    if (isTextareaOutOfBounds()) {
      scrollToBottom();
    }
  }

  function closeEdit() {
    setEditMode('');
    setKey('');
    setValue('');
  }

  function saveVariable() {
    onVariableUpdate(key, value);
    closeEdit();
  }

  function handleOpenAddVariable() {
    setEditMode(MODE.ADD);
  }

  function handleOpenEditVariable(name, value) {
    setEditMode(MODE.EDIT);
    setKey(name);
    setValue(value);
  }

  function scrollToBottom() {
    const scrollableElement = variablesContentRef.current.children[0];
    scrollableElement.scrollTop = scrollableElement.scrollHeight;
  }

  function renderEditButtons({isDisabled}) {
    return (
      <>
        <Styled.EditButton
          title="Exit edit mode"
          data-test="exit-edit-inline-btn"
          onClick={closeEdit}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CloseIcon />}
        />

        <Styled.EditButton
          data-test="save-var-inline-btn"
          title="Save variable"
          disabled={!value || !isValidJSON(value) || isDisabled}
          onClick={saveVariable}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CheckIcon />}
        />
      </>
    );
  }

  renderEditButtons.propTypes = {
    isDisabled: PropTypes.bool
  };

  function renderInlineEdit(propValue) {
    const valueHasntChanged = propValue === value;
    return (
      <>
        <Styled.EditInputTD ref={editInputTDRef}>
          <Styled.EditTextarea
            autoFocus
            hasAutoSize
            minRows={1}
            maxRows={4}
            data-test="edit-value"
            placeholder="Value"
            value={value}
            onChange={e => setValue(e.target.value)}
            onHeightChange={handleHeightChange}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons({
            isDisabled: valueHasntChanged
          })}
        </Styled.EditButtonsTD>
      </>
    );
  }

  function renderInlineAdd() {
    const variableAlreadyExists =
      !!variables.map(variable => variable.name).filter(name => name === key)
        .length > 0;
    const isVariableEmpty = key.trim() === '';
    return (
      <Styled.TR data-test="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            autoFocus
            type="text"
            data-test="add-key"
            placeholder="Variable"
            value={key}
            onChange={e => setKey(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditInputTD>
          <Styled.AddTextarea
            data-test="add-value"
            placeholder="Value"
            hasAutoSize
            minRows={1}
            maxRows={4}
            value={value}
            onChange={e => setValue(e.target.value)}
            onHeightChange={scrollToBottom}
          />
        </Styled.EditInputTD>
        <Styled.AddButtonsTD>
          {renderEditButtons({
            isDisabled: variableAlreadyExists || isVariableEmpty
          })}
        </Styled.AddButtonsTD>
      </Styled.TR>
    );
  }

  function renderContent() {
    return (
      <Styled.TableScroll>
        <Styled.Table>
          <Styled.THead>
            <Styled.TR>
              <Styled.TH>Variable</Styled.TH>
              <Styled.TH>Value</Styled.TH>
              <Styled.TH />
            </Styled.TR>
          </Styled.THead>
          <tbody>
            {variables &&
              variables.map(({name, value: propValue, hasActiveOperation}) => (
                <Styled.TR
                  key={name}
                  data-test={name}
                  hasActiveOperation={hasActiveOperation}
                >
                  <Styled.TD isBold={true}>
                    <Styled.VariableName title={name}>
                      {name}
                    </Styled.VariableName>
                  </Styled.TD>
                  {key === name && editMode === MODE.EDIT && isRunning ? (
                    renderInlineEdit(propValue, name)
                  ) : (
                    <>
                      <Styled.DisplayTextTD>
                        <Styled.DisplayText>{propValue}</Styled.DisplayText>
                      </Styled.DisplayTextTD>
                      {isRunning && (
                        <Styled.EditButtonsTD>
                          {hasActiveOperation ? (
                            <Styled.Spinner />
                          ) : (
                            <Styled.EditButton
                              title="Enter edit mode"
                              data-test="enter-edit-btn"
                              onClick={() =>
                                handleOpenEditVariable(name, propValue)
                              }
                              size="large"
                              iconButtonTheme="default"
                              icon={<Styled.EditIcon />}
                            />
                          )}
                        </Styled.EditButtonsTD>
                      )}
                    </>
                  )}
                </Styled.TR>
              ))}
            {editMode === MODE.ADD && renderInlineAdd()}
          </tbody>
        </Styled.Table>
      </Styled.TableScroll>
    );
  }

  function renderPlaceholder() {
    return (
      <Styled.SkeletonTable>
        <Styled.THead>
          <Styled.TR>
            <Styled.TH>Variable</Styled.TH>
            <Styled.TH>Value</Styled.TH>
            <Styled.TH />
          </Styled.TR>
        </Styled.THead>
        <tbody>
          <Styled.SkeletonTR>
            <Styled.SkeletonTD>{Placeholder()}</Styled.SkeletonTD>
          </Styled.SkeletonTR>
        </tbody>
      </Styled.SkeletonTable>
    );
  }

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {Overlay && Overlay()}
        {!editMode && Placeholder ? renderPlaceholder() : renderContent()}
      </Styled.VariablesContent>
      <Styled.VariablesFooter>
        <Styled.Button
          title="Add variable"
          size="small"
          data-test="enter-add-btn"
          onClick={() => handleOpenAddVariable()}
          disabled={!!editMode || !isEditable || isLoading}
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.VariablesFooter>
    </>
  );
}

Variables.propTypes = {
  isRunning: PropTypes.bool,
  variables: PropTypes.array,
  editMode: PropTypes.string.isRequired,
  isEditable: PropTypes.bool.isRequired,
  onVariableUpdate: PropTypes.func.isRequired,
  setEditMode: PropTypes.func.isRequired,
  isLoading: PropTypes.bool,
  // render props
  Placeholder: PropTypes.func,
  Overlay: PropTypes.func
};
