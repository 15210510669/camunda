/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useRef} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';
import deepEqual from 'fast-deep-equal';

import {
  Button,
  Modal,
  DefinitionSelection,
  BPMNDiagram,
  DiagramScrollLock,
  Tooltip,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './TemplateModal.scss';

export function TemplateModal({
  onClose,
  mightFail,
  templateGroups,
  entity,
  className,
  blankSlate,
  templateToState = (data) => data,
}) {
  const [name, setName] = useState(t(entity + '.new'));
  const [xmlData, setXmlData] = useState([]);
  const [template, setTemplate] = useState(templateGroups[1].templates[0].config);
  const [selectedDefinitions, setSelectedDefinitions] = useState([]);
  const diagramArea = useRef();
  const templateContainer = useRef();

  // load the xml for the selected definitions
  useEffect(() => {
    if (selectedDefinitions.length === 0) {
      return setXmlData([]);
    }

    (async () => {
      const newXmlData = await Promise.all(
        selectedDefinitions.map(({key, name, versions, tenantIds: tenants}) => {
          return (
            xmlData.find(
              (definition) =>
                definition.key === key &&
                deepEqual(versions, definition.versions) &&
                deepEqual(tenants, definition.tenants)
            ) ||
            new Promise((resolve, reject) => {
              mightFail(
                loadProcessDefinitionXml(key, versions[0], tenants[0]),
                (xml) => resolve({key, name, versions, tenants, xml}),
                (error) => reject(showError(error))
              );
            })
          );
        })
      );

      setXmlData(newXmlData);
    })();

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDefinitions, mightFail]);

  // if the selected element gets disabled, select the next enabled element
  useEffect(() => {
    const templates = templateGroups.map(({templates}) => templates).flat();
    const currentlySelectedTemplate = templates.find(({config}) => deepEqual(config, template));

    if (
      selectedDefinitions.length > 0 &&
      currentlySelectedTemplate?.disabled?.(selectedDefinitions)
    ) {
      const enabledTemplate = templates.find(
        (template) => !template.disabled?.(selectedDefinitions) && template.name !== 'blank'
      );

      setTemplate(enabledTemplate.config);
    }
  }, [templateGroups, selectedDefinitions, template]);

  // scroll to the selected element
  useEffect(() => {
    if (selectedDefinitions.length > 0) {
      const activeElement = templateContainer.current?.querySelector('.active');
      activeElement?.scrollIntoView({block: 'nearest', inline: 'nearest'});
    }
  }, [template, selectedDefinitions]);

  const validSelection =
    name && ((xmlData.length > 0 && selectedDefinitions.length > 0) || !template);

  return (
    <Modal
      open
      size="max"
      onClose={onClose}
      className={classnames('TemplateModal', className, {noProcess: !template})}
    >
      <Modal.Header>{t(entity + '.createNew')}</Modal.Header>
      <Modal.Content>
        <div className="definitionSelection">
          <div className="formArea">
            <DefinitionSelection
              type="process"
              expanded
              selectedDefinitions={selectedDefinitions}
              onChange={setSelectedDefinitions}
              versionTooltip={
                selectedDefinitions?.length > 1
                  ? t('templates.disabledMessage.editReport')
                  : undefined
              }
            />
          </div>
          <div className="diagramArea" ref={diagramArea}>
            {xmlData.map(({xml, key, name}, idx) => (
              <div
                key={xmlData.length + idx}
                style={{
                  height:
                    getDiagramHeight(xmlData.length, diagramArea.current?.clientHeight) + 'px',
                }}
                className="diagramContainer"
              >
                <div className="title">{name || key}</div>
                <BPMNDiagram xml={xml} emptyText={t('templates.noXmlHint')} />
                <DiagramScrollLock />
              </div>
            ))}
            {selectedDefinitions.length === 0 && blankSlate}
          </div>
          {!template && <div className="noProcessHint">{t('templates.noProcessHint')}</div>}
        </div>
        <div className="configurationSelection">
          <div className="templateContainer" ref={templateContainer}>
            {templateGroups.map(({name, templates}, idx) => (
              <div key={idx} className="group">
                <div className="groupTitle">{t('templates.templateGroups.' + name)}</div>
                {templates.map(({name, hasSubtitle, img, config, disabled}, idx) => (
                  <Tooltip
                    key={idx}
                    content={
                      disabled?.(selectedDefinitions)
                        ? getDisableStateText(selectedDefinitions)
                        : undefined
                    }
                    position="bottom"
                    align="left"
                  >
                    <div>
                      <Button
                        className={classnames({
                          active: !disabled?.(selectedDefinitions) && deepEqual(template, config),
                          hasSubtitle,
                        })}
                        onClick={() => {
                          setTemplate(config);
                          setName(t(entity + '.templates.' + name));
                        }}
                        disabled={disabled?.(selectedDefinitions)}
                      >
                        {img ? (
                          <img src={img} alt={t(entity + '.templates.' + name)} />
                        ) : (
                          <div className="imgPlaceholder" />
                        )}
                        <div className="name">{t(entity + '.templates.' + name)}</div>
                        {hasSubtitle && (
                          <div className="subTitle">
                            {t(entity + '.templates.' + name + '_subTitle')}
                          </div>
                        )}
                      </Button>
                    </div>
                  </Tooltip>
                ))}
              </div>
            ))}
          </div>
        </div>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Link
          className="Button main primary confirm"
          disabled={!validSelection}
          to={{
            pathname: entity + '/new/edit',
            state: templateToState({
              name,
              template,
              definitions: selectedDefinitions.map((def) => ({...def, displayName: def.name})),
              xml: xmlData[0]?.xml,
            }),
          }}
        >
          {t(entity + '.create')}
        </Link>
      </Modal.Actions>
    </Modal>
  );
}

function getDiagramHeight(count, fullHeight) {
  if (!fullHeight) {
    return;
  }

  if (count === 1) {
    return fullHeight;
  }

  if (count === 2) {
    return 0.5 * fullHeight;
  }

  return 0.425 * fullHeight;
}

function getDisableStateText(selectedDefinitions) {
  if (selectedDefinitions.length === 0) {
    return t('templates.disabledMessage.noProcess');
  }

  if (selectedDefinitions.length === 1) {
    return t('templates.disabledMessage.multipleProcess');
  }

  if (selectedDefinitions.length > 1) {
    return t('templates.disabledMessage.singleProcess');
  }
}

export default withErrorHandling(TemplateModal);
