import React from 'react';
import {shallow} from 'enzyme';
import CollectionsDropdownWithStore from './CollectionsDropdown';
import {Dropdown} from 'components';

const CollectionsDropdown = CollectionsDropdownWithStore.WrappedComponent;

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
  store: {
    collections: [collection]
  },
  entity: processReport,
  entitiesCollections: {reportID: [collection]},
  toggleEntityCollection: jest.fn(),
  setCollectionToUpdate: jest.fn()
};

it('should show for each entity the collection count', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  expect(node.find('.entityCollections').props().label).toBe('1 Collection');
});

it('should show for each report a dropdown with all collections', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  expect(node.find(Dropdown.Option).first()).toIncludeText('aCollectionName');
});

it('should invoke toggleEntityCollection to remove entity from collection when clicking on an option', () => {
  const node = shallow(<CollectionsDropdown {...props} />);
  node
    .find(Dropdown.Option)
    .first()
    .simulate('click');

  expect(props.toggleEntityCollection).toHaveBeenCalledWith(processReport, collection, true);
});

it('should invoke toggleEntityCollection on collections dropdown click to add an entity to collection ', () => {
  const node = shallow(<CollectionsDropdown {...props} entitiesCollections={{}} />);

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

it('should invoke setCollectionToUpdate when clicking Add to new collection', () => {
  const node = shallow(<CollectionsDropdown {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  expect(props.setCollectionToUpdate).toHaveBeenCalledWith({data: {entities: ['reportID']}});
});
