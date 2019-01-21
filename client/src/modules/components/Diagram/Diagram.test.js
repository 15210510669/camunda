import React from 'react';
import {shallow, mount} from 'enzyme';

import {createActivity} from 'modules/testUtils';
import {Colors, ThemeProvider} from 'modules/theme';
import {ACTIVITY_STATE, STATISTICS_OVERLAY_ID} from 'modules/constants';
import {parsedDiagram} from 'modules/utils/bpmn';
import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import {ReactComponent as CanceledDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';

import Diagram from './Diagram';
import DiagramControls from './DiagramControls';
import * as Styled from './styled';

const mockProps = {
  definitions: parsedDiagram.definitions
};

function mountNode(customProps = {}) {
  return mount(
    <ThemeProvider>
      <Diagram {...mockProps} {...customProps} />
    </ThemeProvider>
  );
}

function shallowRenderNode(customProps = {}) {
  return shallow(
    <Diagram.WrappedComponent theme="dark" {...mockProps} {...customProps} />
  );
}

jest.mock('modules/utils/bpmn');

describe('Diagram', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render Diagram with controls', () => {
    // given
    const node = mountNode();

    // then
    const DiagramCanvasNode = node.find(Styled.DiagramCanvas);
    expect(DiagramCanvasNode).toHaveLength(1);
    const DiagramControlsNode = node.find(DiagramControls);
    expect(DiagramControlsNode).toHaveLength(1);
  });

  it('should reinitiate the Viewer when theme changes', async () => {
    // given
    const node = shallowRenderNode({theme: 'dark'});
    const oldViewer = node.instance().Viewer;

    // when
    node.setProps({theme: 'light'});

    // then
    const newViewer = node.instance().Viewer;
    expect(newViewer).not.toBe(oldViewer);
    expect(newViewer.importDefinitions.mock.calls[0][0]).toEqual(
      mockProps.definitions
    );
  });

  it('should reinitiate the Viewer when the definitions change', async () => {
    // given
    const node = shallowRenderNode({definitions: {}, theme: 'dark'});
    const oldViewer = node.instance().Viewer;

    // when
    node.setProps({definitions: mockProps.definitions});

    // then
    const newViewer = node.instance().Viewer;
    expect(newViewer).not.toBe(oldViewer);
    expect(newViewer.importDefinitions.mock.calls[0][0]).toEqual(
      mockProps.definitions
    );
  });

  describe("viewer's theme", () => {
    it('should initiate dark BPMNViewer if theme is dark', () => {
      // given
      const node = shallowRenderNode({theme: 'dark'});
      const nodeInstance = node.instance();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.myRef.current);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiDark02,
        defaultStrokeColor: Colors.darkDiagram
      });
    });

    it('should initiate light BPMNViewer if theme is light', () => {
      // given
      const node = shallowRenderNode({theme: 'light'});
      const nodeInstance = node.instance();

      //then
      const {container, bpmnRenderer} = nodeInstance.Viewer;
      expect(container).toBe(nodeInstance.myRef.current);
      expect(bpmnRenderer).toEqual({
        defaultFillColor: Colors.uiLight04,
        defaultStrokeColor: Colors.uiLight06
      });
    });
  });

  describe('selected flownode marker', () => {
    it('should remove marker when an already selected node gets selected', () => {
      // given
      const node = shallowRenderNode({selectedFlowNode: 'nodeA'});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeB'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
      expect(canvas.removeMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
    });

    it('should remove marker when an already selected node gets selected', () => {
      // given
      const node = shallowRenderNode({selectedFlowNode: null});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeA'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selected');
    });
  });

  describe('selectable flownodes markers', () => {
    it('should add a marker for each selectable flownode', () => {
      // given
      const node = shallowRenderNode({selectableFlowNodes: ['nodeA', 'nodeB']});
      const canvas = node.instance().Viewer.get('canvas');

      // when
      node.setProps({selectedFlowNode: 'nodeA'});

      // then
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeA', 'op-selectable');
      expect(canvas.addMarker).toHaveBeenCalledWith('nodeB', 'op-selectable');
    });
  });

  describe('flownode selection interaction', () => {
    it('should select a selectable flownode that is not selected', () => {
      // given
      const onFlowNodeSelected = jest.fn();
      const node = shallowRenderNode({
        selectableFlowNodes: ['nodeA', 'nodeB'],
        selectedFlowNode: null,
        onFlowNodeSelected
      });

      // when
      node.instance().handleElementClick({element: {id: 'nodeA'}});

      // then
      expect(onFlowNodeSelected).toHaveBeenCalledWith('nodeA');
    });

    it('should deselect a selectable flownode that is already selected', () => {
      // given
      const onFlowNodeSelected = jest.fn();
      const node = shallowRenderNode({
        selectableFlowNodes: ['nodeA', 'nodeB'],
        selectedFlowNode: 'nodeA',
        onFlowNodeSelected
      });

      // when
      node.instance().handleElementClick({element: {id: 'nodeA'}});

      // then
      expect(onFlowNodeSelected).toHaveBeenCalledWith(null);
    });

    it('should deselect current selected flownode if a non-selectable flownode is selected', () => {
      // given
      const onFlowNodeSelected = jest.fn();
      const node = shallowRenderNode({
        selectableFlowNodes: ['nodeA', 'nodeB'],
        selectedFlowNode: 'nodeA',
        onFlowNodeSelected
      });

      // when
      node.instance().handleElementClick({element: {id: 'nodeC'}});

      // then
      expect(onFlowNodeSelected).toHaveBeenCalledWith(null);
    });

    it('should not select a flownode if it is not selectable', () => {
      // given
      const onFlowNodeSelected = jest.fn();
      const node = shallowRenderNode({
        selectableFlowNodes: ['nodeA', 'nodeB'],
        selectedFlowNode: null,
        onFlowNodeSelected
      });

      // when
      node.instance().handleElementClick({element: {id: 'nodeC'}});

      // then
      expect(onFlowNodeSelected).not.toHaveBeenCalled();
    });
  });

  describe('flownode state overlays', () => {
    const flowNodeStateOverlays = [
      createActivity({state: ACTIVITY_STATE.ACTIVE}),
      createActivity({state: ACTIVITY_STATE.INCIDENT}),
      createActivity({state: ACTIVITY_STATE.COMPLETED}),
      createActivity({state: ACTIVITY_STATE.TERMINATED})
    ];

    it('should render flownode state overlays', () => {
      // given
      const node = mountNode({flowNodeStateOverlays});

      // then
      expect(node.find('Overlay')).toHaveLength(flowNodeStateOverlays.length);
      expect(node.find(ActiveIcon)).toHaveLength(1);
      expect(node.find(IncidentIcon)).toHaveLength(1);
      expect(node.find(CompletedDarkIcon)).toHaveLength(1);
      expect(node.find(CanceledDarkIcon)).toHaveLength(1);
    });
  });

  describe('addflowNodesStatisticss', () => {
    it('should statistics overlays with incidents', () => {
      // given
      const flowNodesStatistics = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 0,
          incidents: 7,
          completed: 0
        }
      ];
      const node = mountNode({flowNodesStatistics});

      // then
      const overlayNode = node.find('Overlay');
      expect(overlayNode).toHaveLength(1);
      expect(overlayNode.find(IncidentIcon)).toHaveLength(1);
      expect(overlayNode.contains(7)).toBe(true);
    });

    it('should statistics overlays with active', () => {
      // given
      const flowNodesStatistics = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 7,
          canceled: 0,
          incidents: 0,
          completed: 0
        }
      ];
      const node = mountNode({flowNodesStatistics});

      // then
      const overlayNode = node.find('Overlay');
      expect(overlayNode).toHaveLength(1);
      expect(overlayNode.find(ActiveIcon)).toHaveLength(1);
      expect(overlayNode.contains(7)).toBe(true);
    });

    it('should statistics overlays with completed state', () => {
      // given
      const flowNodesStatistics = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 7
        }
      ];
      const node = mountNode({flowNodesStatistics});

      // then
      const overlayNode = node.find('Overlay');
      expect(overlayNode).toHaveLength(1);
      expect(overlayNode.find(CompletedDarkIcon)).toHaveLength(1);
      expect(overlayNode.contains(7)).toBe(true);
    });

    it('should statistics overlays with canceled state', () => {
      // given
      const flowNodesStatistics = [
        {
          activityId: 'ServiceTask_1un6ye3',
          active: 0,
          canceled: 7,
          incidents: 0,
          completed: 0
        }
      ];
      const node = mountNode({flowNodesStatistics});

      // then
      const overlayNode = node.find('Overlay');
      expect(overlayNode).toHaveLength(1);
      expect(overlayNode.find(CanceledDarkIcon)).toHaveLength(1);
      expect(overlayNode.contains(7)).toBe(true);
    });
  });
});
