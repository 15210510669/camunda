import React from 'react';
import {mount, shallow} from 'enzyme';

import ThemedDashboard from './Dashboard';
import {loadDashboard, remove, update, isAuthorizedToShareDashboard} from './service';

const {WrappedComponent: Dashboard} = ThemedDashboard;

jest.mock('./service', () => {
  return {
    loadDashboard: jest.fn(),
    remove: jest.fn(),
    update: jest.fn(),
    isAuthorizedToShareDashboard: jest.fn()
  };
});

jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>;
    },
    Link: ({children, to, onClick, id}) => {
      return (
        <a id={id} href={to} onClick={onClick}>
          {children}
        </a>
      );
    }
  };
});

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  };
});

jest.mock('react-full-screen', () => ({children}) => <div>{children}</div>);

jest.mock('./AddButton', () => {
  return {AddButton: ({visible}) => <div>AddButton visible: {'' + visible}</div>};
});
jest.mock('./Grid', () => {
  return {Grid: () => <div>Grid</div>};
});
jest.mock('./DimensionSetter', () => {
  return {DimensionSetter: () => <div>DimensionSetter</div>};
});
jest.mock('./DeleteButton', () => {
  return {DeleteButton: () => <button>DeleteButton</button>};
});
jest.mock('./DragBehavior', () => {
  return {DragBehavior: () => <div>DragBehavior</div>};
});
jest.mock('./ResizeHandle', () => {
  return {ResizeHandle: () => <div>ResizeHandle</div>};
});
jest.mock('./AutoRefresh', () => {
  return {
    AutoRefreshBehavior: () => <div>AutoRefreshBehavior</div>,
    AutoRefreshIcon: () => <div>AutoRefreshIcon</div>
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const sampleDashboard = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: [
    {
      id: 1,
      name: 'r1',
      position: {x: 0, y: 0},
      dimensions: {width: 1, height: 1}
    },
    {
      id: 2,
      name: 'r2',
      position: {x: 0, y: 2},
      dimensions: {width: 1, height: 1}
    }
  ]
};

loadDashboard.mockReturnValue(sampleDashboard);
isAuthorizedToShareDashboard.mockReturnValue(true);

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it("should show an error page if dashboard doesn't exist", async () => {
  const node = await mount(shallow(<Dashboard {...props} />).get(0));

  await node.setState({
    serverError: 404
  });

  expect(node).toIncludeText('error page');
});

it('should display a loading indicator', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));

  expect(node.find('.sk-circle')).toBePresent();
});

it('should initially load data', () => {
  mount(<Dashboard {...props} />);

  expect(loadDashboard).toHaveBeenCalled();
});

it('should display the key properties of a dashboard', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));

  node.setState({
    loaded: true,
    ...sampleDashboard
  });

  expect(node).toIncludeText(sampleDashboard.name);
  expect(node).toIncludeText(sampleDashboard.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node.find('.Dashboard__edit-button')).toBePresent();
});

it('should remove a dashboard when delete button is clicked', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  node
    .find('.Dashboard__delete-dashboard-modal-button')
    .first()
    .simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the dashboard list on dashboard deletion', async () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  await node
    .find('.Dashboard__delete-dashboard-modal-button')
    .first()
    .simulate('click');

  expect(node).toIncludeText('REDIRECT to /dashboards');
});

it('should render a sharing popover', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node.find('.Dashboard__share-button').first()).toIncludeText('Share');
});

it('should enter fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(true);
});

it('should leave fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(false);
});

it('should activate auto refresh mode and set it to numeric value', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  node
    .find('.Dashboard__autoRefreshOption')
    .last()
    .simulate('click');

  expect(typeof node.state('autoRefreshInterval')).toBe('number');
});

it('should deactivate autorefresh mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 1000});

  node
    .find('.Dashboard__autoRefreshOption')
    .first()
    .simulate('click');

  expect(node.state('autoRefreshInterval')).toBe(null);
});

it('should add an autorefresh addon when autorefresh mode is active', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 1000});

  expect(node).toIncludeText('Addons: AutoRefreshBehavior');
});

it('should invoke the renderDashboard function after the interval duration ends', async () => {
  jest.useFakeTimers();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 600});

  node.instance().renderDashboard = jest.fn();
  node.update();
  node.instance().setAutorefresh(600)();
  jest.runTimersToTime(700);
  expect(await node.instance().renderDashboard).toHaveBeenCalledTimes(1);
});

it('should have a toggle theme button that is only visible in fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node).not.toIncludeText('Toggle Theme');

  node.setState({fullScreenActive: true});

  expect(node).toIncludeText('Toggle Theme');
});

it('should toggle the theme when clicking the toggle theme button', () => {
  const spy = jest.fn();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});
  node.setProps({toggleTheme: spy});

  node
    .find('button')
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when exiting fullscreen mode', () => {
  const spy = jest.fn();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});
  node.setProps({toggleTheme: spy, theme: 'dark'});

  node.instance().toggleFullscreen();

  expect(spy).toHaveBeenCalled();
});

describe('edit mode', async () => {
  it('should provide a link to view mode', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node.find('.Dashboard__save-button')).toBePresent();
    expect(node.find('.Dashboard__cancel-button')).toBePresent();
    expect(node.find('.Dashboard__edit-button')).not.toBePresent();
  });

  it('should provide name edit input', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    expect(node.find('input#name')).toBePresent();
  });

  it('should invoke update on save click', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    node.find('.Dashboard__save-button').simulate('click');

    expect(update).toHaveBeenCalled();
  });

  it('should update name on input change', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});
    expect(node).toHaveState('name', input);
  });

  it('should reset name on cancel', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, name: 'test name', originalName: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    node.find('.Dashboard__cancel-button').simulate('click');
    expect(node).toHaveState('name', 'test name');
  });

  it('should contain a Grid', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('Grid');
  });

  it('should contain an AddButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('AddButton');
  });

  it('should contain a DeleteButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('DeleteButton');
  });

  it('should hide the AddButton based on the state', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, addButtonVisible: false});

    expect(node).toIncludeText('AddButton visible: false');
  });

  it('should add DragBehavior', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('DragBehavior');
  });

  it('should add a resize handle', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('ResizeHandle');
  });

  it('should disable the save button and highlight the input if name empty', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true,
      name: ''
    });

    expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
    expect(node.find('.Dashboard__save-button')).toBeDisabled();
  });

  it('should disable the share button if not authorized', () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true,
      name: '',
      isAuthorizedToShare: false
    });

    const shareButton = node.find('.Dashboard__share-button');
    expect(shareButton).toBeDisabled();
    expect(shareButton.props()).toHaveProperty(
      'tooltip',
      "You are not authorized to share the dashboard,  because you don't have access to all reports on the dashboard!"
    );
  });

  it('should enable share button if authorized', () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true,
      name: '',
      isAuthorizedToShare: true
    });

    const shareButton = node.find('.Dashboard__share-button');
    expect(shareButton).not.toBeDisabled();
  });

  // re-enable this test once https://github.com/airbnb/enzyme/issues/1604 is fixed
  // it('should select the name input field if dashboard is just created', () => {
  //   props.match.params.viewMode = 'edit';
  //   props.location.search = '?new';

  //   const node = mount(shallow(<Dashboard {...props} />).get(0))

  //   node.setState({
  //     loaded: true,
  //     ...sampleDashboard
  //   });

  //   expect(
  //     node
  //       .find('.Dashboard__name-input')
  //       .at(0)
  //       .getDOMNode()
  //   ).toBe(document.activeElement);
  // });
});
