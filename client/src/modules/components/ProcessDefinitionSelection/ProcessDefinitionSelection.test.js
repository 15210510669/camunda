import React from 'react';
import {mount} from 'enzyme';

import ProcessDefinitionSelection from './ProcessDefinitionSelection';

jest.mock('components', () => {
  const Select = props => <select id="selection" {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Select,
    BPMNDiagram: props => <div id='diagram'>Diagram {props.children} {props.xml}</div>
  };
});

const spy = jest.fn();

const props = {
  loadProcessDefinitions: jest.fn(),
  onChange: spy
}

props.loadProcessDefinitions.mockReturnValue(
  [
    {key:'foo', 
      versions: [
        {id:'procdef2', key: 'foo', version: 2},      
        {id:'procdef1', key: 'foo', version: 1}
      ]
    },
    {key:'bar', 
      versions: [
        {id:'anotherProcDef', key: 'bar', version: 1}
      ]
    }

  ]
);


it('should render without crashing', () => {
  mount(<ProcessDefinitionSelection {...props}/>);
});

it('should display a loading indicator', () => {
  const node = mount(<ProcessDefinitionSelection {...props}/>);

  expect(node.find('.ProcessDefinitionSelection__loading-indicator')).toBePresent();
});

it('should initially load all process definitions', () => {
  props.loadProcessDefinitions.mockClear();
  mount(<ProcessDefinitionSelection {...props}/>);

  expect(props.loadProcessDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  const node = await mount(<ProcessDefinitionSelection {...props} />);
  await node.update();

  await node.instance().changeKey({target: {value:'foo'}});

  expect(node.state().version).toBe(2);
});


it('should update definition if versions is changed', async () => {
  const node = await mount(<ProcessDefinitionSelection {...props}/>);
  await node.update();

  await node.instance().changeKey({target: {value:'foo'}});
  await node.instance().changeVersion({target: {value:'1'}});

  expect(node.state().id).toBe('procdef1');
});

it('should set id, key and version, if process definition is already available', async () => {
  const definitionConfig = {
    processDefinitionId: 'procdef2',
    processDefinitionKey: 'foo',
    processDefinitionVersion: 2
  };
  const node = await mount(<ProcessDefinitionSelection {...definitionConfig} {...props} />);
  await node.update();

  expect(node.state().id).toBe('procdef2');
  expect(node.state().key).toBe('foo');
  expect(node.state().version).toBe(2);    
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    processDefinitionId: 'procdef2',
    processDefinitionKey: 'foo',
    processDefinitionVersion: 2
  };
  const node = await mount(<ProcessDefinitionSelection {...definitionConfig} {...props} />);
  await node.update();

  await node.instance().changeVersion({target: {value:'1'}});

  expect(spy).toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const node = await mount(<ProcessDefinitionSelection renderDiagram={true} processDefinitionId={'procdef2'} {...props} />);
  await node.update();


  expect(node).toIncludeText('Diagram');
});

it('should disable version selection, if no key is selected', async () => {
  const node = await mount(<ProcessDefinitionSelection {...props} />);
  await node.update();

  const versionSelect= node.find('select[name="ProcessDefinitionSelection__version"]');
  expect(versionSelect.prop("disabled")).toBeTruthy();
});

it('should display all option in version selection if enabled', async () => {
  const node = await mount(<ProcessDefinitionSelection enableAllVersionSelection={true} processDefinitionId={'procdef2'} {...props} />);
  await node.update();

  expect(node.find('option[value="ALL"]').text()).toBe('all');
});

it('should not display all option in version selection if disabled', async () => {
  const node = await mount(<ProcessDefinitionSelection enableAllVersionSelection={false} processDefinitionId={'procdef2'} {...props} />);
  await node.update();

  expect(node.find('option[value="ALL"]').exists()).toBe(false);
});

it('should set latest process definition if verions field is set to all', async () => {
  const node = await mount(<ProcessDefinitionSelection {...props}/>);
  await node.update();

  await node.instance().changeKey({target: {value:'foo'}});
  await node.instance().changeVersion({target: {value:'1'}});

  await node.instance().changeVersion({target: {value:'ALL'}});

  expect(node.state().version).toBe('ALL');
  expect(node.state().id).toBe('procdef2');
});