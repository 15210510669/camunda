/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, override} from 'mobx';
import {ProcessXmlBase} from './processXml.base';
import {parseDiagramXML} from 'modules/utils/bpmn';

class ProcessesXml extends ProcessXmlBase {
  constructor() {
    super();

    makeObservable(this, {
      setProcessXml: action,
      selectableFlowNodes: override,
    });
  }

  get selectableFlowNodes() {
    return super.selectableFlowNodes
      .filter((flowNode) => {
        return ['bpmn:ServiceTask', 'bpmn:UserTask'].includes(flowNode.$type);
      })
      .map((flowNode) => {
        return {...flowNode, name: flowNode.name ?? flowNode.id};
      });
  }

  setProcessXml = async (xml: string | null) => {
    if (xml === null) {
      return;
    }

    const diagramModel = await parseDiagramXML(xml);
    this.handleFetchXmlSuccess(xml, diagramModel);
  };
}

const processXmlStore = new ProcessesXml();

export {processXmlStore};
