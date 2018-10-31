import React from 'react';
import {shallow} from 'enzyme';

import ShareEntity from './ShareEntity';

const props = {
  shareEntity: jest.fn(),
  revokeEntitySharing: jest.fn(),
  getSharedEntity: jest.fn()
};

beforeAll(() => {
  const windowProps = JSON.stringify(window.location);
  delete window.location;

  Object.defineProperty(window, 'location', {
    value: JSON.parse(windowProps)
  });
  window.location.origin = 'http://example.com';
});

it('should render without crashing', () => {
  shallow(<ShareEntity {...props} />);
});

it('should initially get already shared entities', () => {
  shallow(<ShareEntity {...props} />);

  expect(props.getSharedEntity).toHaveBeenCalled();
});

it('should share entity if is checked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = shallow(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: true}});

  expect(props.shareEntity).toHaveBeenCalled();
});

it('should delete entity if sharing is revoked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = shallow(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: false}});

  expect(props.revokeEntitySharing).toHaveBeenCalled();
});

it('should construct special link', () => {
  const node = shallow(<ShareEntity type="report" {...props} />);

  node.setState({loaded: true, id: 10});

  expect(node.find('.ShareEntity__share-link')).toHaveProp(
    'value',
    'http://example.com/#/share/report/10'
  );
});

it('should construct special link for embedding', () => {
  const node = shallow(<ShareEntity type="report" {...props} />);
  Object.defineProperty(window.location, 'origin', {
    value: 'http://example.com'
  });

  node.setState({loaded: true, id: 10});

  expect(node.find('.ShareEntity__embed-link').prop('value')).toContain(
    '<iframe src="http://example.com/#/share/report/10'
  );
});

it('should display a loading indicator', () => {
  const node = shallow(<ShareEntity {...props} />);

  expect(node.find('LoadingIndicator')).toBePresent();
});
