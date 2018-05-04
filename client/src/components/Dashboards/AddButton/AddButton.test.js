import React from 'react';
import {mount} from 'enzyme';

import AddButton from './AddButton';
import {loadReports} from '../service';

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Select,
    Input: props => <input {...props}>{props.children}</input>,
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>,
    DashboardObject: ({children}) => <div className="DashboardObject">{children}</div>
  };
});

jest.mock('../service', () => {
  return {
    loadReports: jest.fn().mockReturnValue([]),
    getOccupiedTiles: jest
      .fn()
      .mockReturnValue({0: {0: true}, 1: {0: true}, 2: {0: true}, 3: {0: true}, 4: {0: true}})
  };
});

const props = {
  tileDimensions: {
    columns: 18
  },
  reports: []
};

it('should load the available reports', () => {
  mount(<AddButton {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should open a modal on click', () => {
  const node = mount(<AddButton {...props} />);

  node.find('Button.AddButton').simulate('click');

  expect(node.find('#modal_header')).toIncludeText('Add a Report');
});

it('should render a select element with the available reports as options', () => {
  const node = mount(<AddButton {...props} />);

  node.setState({
    modalOpen: true,
    availableReports: [
      {
        id: 'a',
        name: 'Report A'
      },
      {
        id: 'b',
        name: 'Report B'
      }
    ]
  });

  expect(node.find('select')).toBePresent();
  expect(node.find('select')).toIncludeText('Please select...');
  expect(node.find('select')).toIncludeText('Report A');
  expect(node.find('select')).toIncludeText('Report B');
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = mount(<AddButton {...props} addReport={spy} />);

  node.setState({
    modalOpen: true,
    availableReports: [
      {
        id: 'a',
        name: 'Report A'
      },
      {
        id: 'b',
        name: 'Report B'
      }
    ],
    selectedReportId: 'a'
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    dimensions: {
      height: 4,
      width: 6
    },
    position: {
      x: 5,
      y: 0
    },
    id: 'a'
  });
});

it('should place the addButton where is no Report', () => {
  const node = mount(
    <AddButton
      {...props}
      reports={[
        {
          position: {x: 0, y: 0},
          dimensions: {width: 3, height: 1},
          id: '1'
        },
        {
          position: {x: 2, y: 0},
          dimensions: {width: 1, height: 4},
          id: '2'
        },
        {
          position: {x: 3, y: 1},
          dimensions: {width: 2, height: 2},
          id: '3'
        }
      ]}
    />
  );

  const addButtonPosition = node.instance().getAddButtonPosition();

  expect(addButtonPosition.x).toBe(5);
  expect(addButtonPosition.y).toBe(0);
});

it('should render nothing if visible is set to false', () => {
  const node = mount(<AddButton {...props} visible={false} />);

  expect(node.getDOMNode()).toBe(null);
});

it('should show only "No reports created yet" option if no reports are available', () => {
  const node = mount(<AddButton {...props} />);

  node.setState({
    modalOpen: true
  });

  expect(node.find('select')).toIncludeText('No reports created yet');
  expect(node.find('select')).not.toIncludeText('Please select...');
});

it("should truncate report name if it's longer than 50 signs", async () => {
  loadReports.mockReturnValueOnce([
    {
      id: 'anId',
      name: 'a super long name that should be definitely longer than 50 signs.'
    }
  ]);

  const node = await mount(<AddButton {...props} />);
  node.setState({
    modalOpen: true
  });

  expect(
    node
      .find('select')
      .find('option')
      .at(1)
      .text().length
  ).toBeLessThanOrEqual(50);

  loadReports.mockReturnValue([]);
});

it('should contain an Add External Source field', () => {
  const node = mount(<AddButton {...props} />);

  node.setState({
    modalOpen: true
  });

  expect(node).toIncludeText('Add External Source');
});

it('should render an input field when switching to external mode', () => {
  const node = mount(<AddButton {...props} />);

  node.setState({
    modalOpen: true
  });

  node.find('.AddButton__externalSourceLink').simulate('click');

  expect(node.find('input.AddButton__externalSourceInput')).toBePresent();
});

it('should call the callback when adding an external source', () => {
  const spy = jest.fn();
  const node = mount(<AddButton {...props} addReport={spy} />);

  node.setState({
    modalOpen: true,
    externalSourceMode: true,
    externalSource: 'externalStuff'
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    dimensions: {
      height: 4,
      width: 6
    },
    position: {
      x: 5,
      y: 0
    },
    id: '',
    configuration: {
      external: 'externalStuff'
    }
  });
});
