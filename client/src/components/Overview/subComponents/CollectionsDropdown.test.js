import React from 'react';
import {shallow} from 'enzyme';
import CollectionsDropdown from './CollectionsDropdown';
import {Dropdown} from 'components';

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport]
  }
};

const props = {
  collections: [collection],
  entityCollections: [collection],
  entity: processReport,
  toggleEntityCollection: jest.fn(),
  createCollectionWithEntity: jest.fn()
};

it('should show for each entity the collection count', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  expect(node.find('.entityCollections').props().label).toBe('1 Collection');
});

it('should show for each report a dropdown with all collections', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  expect(node.find(Dropdown.Option).first()).toIncludeText('aCollectionName');
});

it('should invok toggleEntityCollection to remove entity from collection when clicking on an option', () => {
  const node = shallow(<CollectionsDropdown {...props} />);
  node
    .find(Dropdown.Option)
    .first()
    .simulate('click');

  expect(props.toggleEntityCollection).toHaveBeenCalledWith(processReport, collection, true);
});

it('should invok toggleEntityCollection on collections dropdown click to add an entity to collection ', () => {
  const node = shallow(<CollectionsDropdown {...props} entityCollections={[]} />);

  node
    .find(Dropdown.Option)
    .first()
    .simulate('click');

  expect(props.toggleEntityCollection).toHaveBeenCalledWith(processReport, collection, false);
});

it('should show the current collection on the top of the dropdown list', () => {
  const testCollection = {id: 'test', name: 'test'};
  const node = shallow(
    <CollectionsDropdown
      {...props}
      collections={[collection, testCollection]}
      currentCollection={testCollection}
    />
  );

  expect(node.find(Dropdown.Option).first()).toIncludeText('test');
});

it('should invok createCollectionWithEntity when clicking Add to new collection', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  expect(props.createCollectionWithEntity).toHaveBeenCalledWith({data: {entities: ['reportID']}});
});
