import React from 'react';
import {shallow, mount} from 'enzyme';

import {DEFAULT_FILTER, FILTER_TYPES} from 'modules/constants';
import Button from 'modules/components/Button';
import {mockResolvedAsyncFn} from 'modules/testUtils';
import * as api from 'modules/api/instances/instances';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import Filters from './Filters';
import * as Styled from './styled';
import {ALL_VERSIONS_OPTION} from './constants';

const COMPLETE_FILTER = {
  ...DEFAULT_FILTER,
  ids: 'a, b, c',
  errorMessage: 'This is an error message',
  startDate: '08 October 2018',
  endDate: '10-10-2018',
  workflow: 'demoProcess',
  version: '2',
  activityId: '4'
};

const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
];

// transformed groupedWorkflowsMock in an object structure
const workflows = {
  demoProcess: {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  orderProcess: {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
};

api.fetchGroupedWorkflows = mockResolvedAsyncFn(groupedWorkflowsMock);

describe('Filters', () => {
  const spy = jest.fn();
  const resetSpy = jest.fn();
  const mockProps = {
    onFilterChange: spy,
    onFilterReset: resetSpy,
    filterCount: 1,
    activityIds: []
  };

  const mockPropsWithActivityIds = {
    onFilterChange: spy,
    onFilterReset: resetSpy,
    filterCount: 1,
    activityIds: [
      {value: 'taskA', label: 'task A'},
      {value: 'taskB', label: 'taskB'}
    ]
  };

  beforeEach(() => {
    spy.mockClear();
    resetSpy.mockClear();
  });

  it('should render with the right initial state', () => {
    // given
    const node = shallow(
      <Filters
        groupedWorkflows={workflows}
        {...mockProps}
        filter={DEFAULT_FILTER}
      />
    );

    // then
    expect(node.state().filter.activityId).toEqual('');
    expect(node.state().filter.workflow).toEqual('');
    expect(node.state().filter.version).toEqual('');
    expect(node.state().filter.startDate).toEqual('');
    expect(node.state().filter.endDate).toEqual('');
    expect(node.state().filter.ids).toEqual('');
    expect(node.state().filter.errorMessage).toEqual('');
  });

  it('should render the running and finished filters', () => {
    // given
    const {active, incidents, completed, canceled} = DEFAULT_FILTER;

    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Filters
            groupedWorkflows={workflows}
            {...mockProps}
            filter={DEFAULT_FILTER}
          />
        </CollapsablePanelProvider>
      </ThemeProvider>
    );
    const FilterNodes = node.find(Styled.CheckboxGroup);

    // then
    expect(FilterNodes).toHaveLength(2);
    expect(FilterNodes.at(0).prop('type')).toBe(FILTER_TYPES.RUNNING);
    expect(FilterNodes.at(0).prop('filter')).toEqual({active, incidents});
    expect(FilterNodes.at(0).prop('onChange')).toBe(mockProps.onFilterChange);
    expect(FilterNodes.at(1).prop('type')).toBe(FILTER_TYPES.FINISHED);
    expect(FilterNodes.at(1).prop('filter')).toEqual({completed, canceled});
    expect(FilterNodes.at(1).prop('onChange')).toBe(mockProps.onFilterChange);
  });

  describe('errorMessage filter', () => {
    it('should render an errorMessage field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'errorMessage');
      const onBlur = field.props().onBlur;
      onBlur({target: {value: '', name: 'errorMessage'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.prop('placeholder')).toEqual('Error Message');
      expect(field.prop('value')).toEqual('');
      expect(spy).toHaveBeenCalledWith({errorMessage: ''});
    });

    // test behaviour here
    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      expect(node.state().filter.errorMessage).toEqual('');
    });

    it('should be prefilled with the value from props.filter.errorMessage ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'errorMessage');

      // then
      expect(field.props().value).toEqual('This is an error message');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      node.instance().handleInputChange({
        target: {value: 'error message', name: 'errorMessage'}
      });

      expect(node.state().filter.errorMessage).toEqual('error message');
    });

    it('should call onFilterChange with the right error message', () => {
      const errorMessage = 'lorem ipsum';
      // given
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleFieldChange({
        target: {value: errorMessage, name: 'errorMessage'}
      });

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage});
    });

    it('should call onFilterChange with empty error message', () => {
      // given
      // user blurs without writing
      const emptyErrorMessage = '';
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleFieldChange({
        target: {value: emptyErrorMessage, name: 'errorMessage'}
      });

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage: ''});
    });
  });

  describe('ids filter', () => {
    it('should render an ids field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Textarea)
        .filterWhere(n => n.props().name === 'ids');
      const onBlur = field.props().onBlur;

      // when
      onBlur({target: {value: '', name: 'ids'}});

      // then
      expect(field).toExist();
      expect(field.prop('value')).toEqual('');
      expect(field.prop('placeholder')).toEqual(
        'Instance Id(s) separated by space or comma'
      );
      expect(spy).toHaveBeenCalledWith({ids: ''});
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      expect(node.state().filter.ids).toEqual('');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      node.instance().handleInputChange({
        target: {value: 'aa, ab, ac', name: 'ids'}
      });

      expect(node.state().filter.ids).toEqual('aa, ab, ac');
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Textarea)
        .filterWhere(n => n.props().name === 'ids');

      // then
      expect(field.props().value).toEqual('a, b, c');
    });

    it('should call onFilterChange with the right instance ids', () => {
      const instanceIds = '4294968008,4294972032  4294974064, 4294976280, ,';
      // given
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node
        .instance()
        .handleFieldChange({target: {value: instanceIds, name: 'ids'}});

      // then
      expect(spy).toHaveBeenCalledWith({ids: instanceIds});
    });

    it('should call onFilterChange with an empty array', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node
        .instance()
        .handleFieldChange({target: {value: emptyInstanceIds, name: 'ids'}});

      // then
      expect(spy).toHaveBeenCalledWith({
        ids: ''
      });
    });
  });

  describe('workflow filter', () => {
    it('should render an workflow select field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '', name: 'workflow'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow');
      expect(spy).toHaveBeenCalledWith({
        workflow: '',
        activityId: '',
        version: ''
      });
    });

    it('should render the value from this.props.filter.workflow', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      // then
      expect(field.props().value).toEqual('demoProcess');
    });

    it('should have values read from this.props.groupedWorkflows', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      expect(field.props().options).toEqual([
        {value: 'demoProcess', label: 'New demo process'},
        {value: 'orderProcess', label: 'Order'}
      ]);
    });

    it('should update state with selected option', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(node.state().filter.workflow).toEqual(value);
    });

    if (('should update filter value in instances page', () => {}));
  });

  describe('version filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '1'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow Version');
      expect(spy).toHaveBeenCalled();
    });

    it('should render the value from this.props.filter.version', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />{' '}
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      // then
      expect(field.props().value).toEqual('2');
    });

    it('should display the latest version of a selected workflowName', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');
      // then
      expect(field.props().value).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
      expect(spy.mock.calls[0][0].version).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
    });

    it('should display an all versions option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const options = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version')
        .props().options;

      // then
      expect(options[0].label).toEqual('Version 3');
      expect(options[options.length - 1].value).toEqual(ALL_VERSIONS_OPTION);
      expect(options[options.length - 1].label).toEqual('All versions');
      // groupedWorkflowsMock.workflows.length + 1 (All versions)
      expect(options.length).toEqual(4);
    });

    it('should not allow the selection of the first option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      versionField.prop('onChange')({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(
        node
          .find(Styled.Select)
          .filterWhere(n => n.props().name === 'version')
          .props().value
      ).toEqual(String(groupedWorkflowsMock[0].workflows[0].version));
      // should update the workflow in Instances
      expect(spy.mock.calls.length).toEqual(1);
    });

    it('should reset after a the workflowName field is also reseted ', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      workflowField.prop('onChange')({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(
        node
          .find(Styled.Select)
          .filterWhere(n => n.props().name === 'version')
          .props().value
      ).toEqual('');
      expect(
        node
          .find(Styled.Select)
          .filterWhere(n => n.props().name === 'workflow')
          .props().value
      ).toEqual('');
    });

    it('should call onFilterChange when a workflow version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({
        workflow: 'demoProcess',
        version: '3',
        activityId: ''
      });
    });

    it('should call onFilterChange when all workflow versions are selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();
      node
        .instance()
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});

      // then
      expect(spy).toHaveBeenCalled();
      expect(spy.mock.calls[0][0].version).toEqual('3');
      expect(spy.mock.calls[0][0].activityId).toBe('');
      expect(spy.mock.calls[1][0].version).toEqual('all');
      expect(spy.mock.calls[1][0].activityId).toBe('');
    });
  });

  describe('activityId filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');
      const onChange = field.props().onChange;

      //when
      onChange({target: {value: '', name: 'activityId'}});
      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Flow Node');
      expect(field.props().disabled).toBe(true);
      expect(field.props().options.length).toBe(0);
      expect(spy).toHaveBeenCalledWith({activityId: ''});
    });

    it('should render the value from this.props.filter.activityId', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().value).toEqual('4');
    });

    it('should be disabled if All versions is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({target: {value: ALL_VERSIONS_OPTION}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
    });

    it('should not be disabled when a version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({
        target: {value: groupedWorkflowsMock[0].workflows[0].version}
      });
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(false);
      expect(field.props().value).toEqual('');
    });

    it('should read the options from this.props.activityIds', () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockPropsWithActivityIds}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      expect(field.props().options[0].value).toEqual('taskA');
      expect(field.props().options[1].value).toEqual('taskB');
    });

    it('should be disabled after the workflow name is reseted', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      workflowField.prop('onChange')({target: {value: ''}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
      expect(field.props().options.length).toEqual(0);
    });

    it('should display a list of activity ids', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockPropsWithActivityIds}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().options.length).toEqual(2);
    });

    it('should set the state on activityId selection', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const activityId = mockPropsWithActivityIds.activityIds[0].value;
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockPropsWithActivityIds}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      node.instance().handleFieldChange({
        target: {
          value: mockPropsWithActivityIds.activityIds[0].value,
          name: 'activityId'
        }
      });

      // then
      expect(node.state().filter.activityId).toEqual(activityId);
    });
  });

  describe('startDate filter', () => {
    const target = {value: '08 October 1084', name: 'startDate'};
    it('should exist', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'startDate');
      const onBlur = field.props().onBlur;
      onBlur({target});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().placeholder).toEqual('Start Date');
      expect(field.props().value).toEqual('');
      expect(spy).toHaveBeenCalledWith({startDate: '08 October 1084'});
    });

    it('should be prefilled with the value from props.filter.startDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'startDate');

      // then
      expect(field.props().value).toEqual('08 October 2018');
    });

    //change without implementation
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: '25 January 2009', name: 'startDate'}
      });
      node.update();

      expect(node.state().filter.startDate).toEqual('25 January 2009');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleFieldChange({
        target: {value: '25 January 2009', name: 'startDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalled();
      expect(spy.mock.calls[0][0].startDate).toBe('25 January 2009');
    });

    it('should send null values for empty start dates', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleFieldChange({
        target: {value: '', name: 'startDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({startDate: ''});
    });
  });

  describe('endDate filter', () => {
    it('should exist', () => {
      // given
      const target = {value: '08 October 1984', name: 'endDate'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'endDate');
      const onBlur = field.props().onBlur;
      onBlur({target});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().name).toEqual('endDate');
      expect(field.props().placeholder).toEqual('End Date');
      expect(field.props().value).toEqual('');
      expect(spy).toHaveBeenCalledWith({endDate: '08 October 1984'});
    });

    it('should be prefilled with the value from props.filter.endDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      //when
      const field = node
        .find(Styled.TextInput)
        .filterWhere(n => n.props().name === 'endDate');

      // then
      expect(field.props().value).toEqual('10-10-2018');
    });

    // change
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: '25 January 2009', name: 'endDate'}
      });
      node.update();

      // then
      expect(node.state().filter.endDate).toEqual('25 January 2009');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER}
        />
      );

      //when
      node.instance().handleFieldChange({
        target: {value: '25 January 2009', name: 'endDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalled();
      expect(spy.mock.calls[0][0].endDate).toBe('25 January 2009');
    });
  });

  describe('reset button', () => {
    it('should render the reset filters button', () => {
      // given filter is different from DEFAULT_FILTER
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);
      const onClick = ResetButtonNode.props().onClick;

      //when
      onClick();

      // then
      expect(ResetButtonNode.text()).toBe('Reset Filters');
      expect(ResetButtonNode).toHaveLength(1);
      expect(ResetButtonNode.prop('disabled')).toBe(false);
      expect(resetSpy).toHaveBeenCalled();
    });

    it('should render the disabled reset filters button', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      // then
      expect(ResetButtonNode).toHaveLength(1);
      expect(ResetButtonNode.prop('disabled')).toBe(true);
    });

    it('should reset all fields', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      // click reset filters
      ResetButtonNode.simulate('click');
      node.update();

      // then
      expect(node.find('select[name="workflow"]').get(0).props.value).toBe('');
      expect(node.find('select[name="version"]').get(0).props.value).toBe('');
      expect(node.find('textarea[name="ids"]').get(0).props.value).toBe('');
      expect(node.find('input[name="errorMessage"]').get(0).props.value).toBe(
        ''
      );
      expect(node.find('input[name="startDate"]').get(0).props.value).toBe('');
      expect(node.find('input[name="endDate"]').get(0).props.value).toBe('');
      expect(node.find('select[name="activityId"]').get(0).props.value).toBe(
        ''
      );
    });

    it('should call this.props.onFilterReset', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      //when
      ResetButtonNode.simulate('click');
      node.update();

      // then
      expect(resetSpy).toHaveBeenCalled();
    });
  });
});
