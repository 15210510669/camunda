import bpmnJs from 'bpmn-js';

/**
 * Utility that makes a call with a workflow xml for worklow nodes
 * @param {String} xml
 * @return: a Promise that when resolves returns the list of the workflow nodes
 */
export function parseDiagramXML(xml) {
  bpmnJs.prototype.options = {};
  const moddle = bpmnJs.prototype._createModdle({});

  return new Promise((resolve, reject) => {
    moddle.fromXML(xml, 'bpmn:Definitions', function(
      err,
      definitions,
      context
    ) {
      if (err) {
        reject(err);
      }
      resolve({definitions, bpmnElements: context.elementsById});
    });
  });
}
