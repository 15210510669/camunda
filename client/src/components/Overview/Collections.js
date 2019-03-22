import React from 'react';
import {Button} from 'components';

import UpdateCollectionModal from './subComponents/UpdateCollectionModal';
import CollectionItem from './subComponents/CollectionItem';
import ReportItem from './subComponents/ReportItem';

import './Collections.scss';
import DashboardItem from './subComponents/DashboardItem';

import {withStore} from './OverviewStore';

class Collections extends React.Component {
  state = {
    showAllId: null
  };

  isDashboard = entity => {
    return entity.reports;
  };

  render() {
    const {updating, collections, searchQuery} = this.props.store;

    const empty = collections.length === 0 && (
      <div className="collectionBlankSlate">
        <strong>Group Reports and Dashboards into Collections.</strong> <br />
        <Button
          type="link"
          className="createLink"
          onClick={() => this.props.setCollectionToUpdate({})}
        >
          Create a Collection…
        </Button>
      </div>
    );

    const filteredCollections = collections.filter(collection =>
      collection.name.toLowerCase().includes(searchQuery)
    );

    const noSearchResult = !empty && filteredCollections.length === 0 && (
      <p className="empty">No Collections matching '{searchQuery}'</p>
    );

    return (
      <div className="Collections">
        <ul className="entityList">
          {empty}
          {noSearchResult}
          {filteredCollections.map(collection => (
            <CollectionItem
              key={collection.id}
              searchQuery={searchQuery}
              collection={collection}
              setCollectionToUpdate={this.props.setCollectionToUpdate}
              showDeleteModalFor={this.props.showDeleteModalFor}
            >
              {collection.data.entities.length > 0 ? (
                <ul className="entityList">
                  {collection.data.entities
                    .slice(0, this.state.showAllId === collection.id ? undefined : 5)
                    .map(entity =>
                      this.isDashboard(entity) ? (
                        <DashboardItem
                          key={entity.id}
                          dashboard={entity}
                          collection={collection}
                          duplicateEntity={this.props.duplicateEntity}
                          showDeleteModalFor={this.props.showDeleteModalFor}
                        />
                      ) : (
                        <ReportItem
                          key={entity.id}
                          report={entity}
                          collection={collection}
                          showDeleteModalFor={this.props.showDeleteModalFor}
                          duplicateEntity={this.props.duplicateEntity}
                        />
                      )
                    )}
                </ul>
              ) : (
                <p className="empty">There are no items in this Collection.</p>
              )}
              <div className="showAll">
                {!this.state.loading &&
                  collection.data.entities.length > 5 &&
                  (this.state.showAllId !== collection.id ? (
                    <>
                      {collection.data.entities.length} Items.{' '}
                      <Button type="link" onClick={() => this.setState({showAllId: collection.id})}>
                        Show all...
                      </Button>
                    </>
                  ) : (
                    <Button type="link" onClick={() => this.setState({showAllId: null})}>
                      Show less...
                    </Button>
                  ))}
              </div>
            </CollectionItem>
          ))}
        </ul>
        {updating && (
          <UpdateCollectionModal
            collection={updating}
            onClose={() => this.props.setCollectionToUpdate(null)}
            onConfirm={this.props.updateOrCreateCollection}
          />
        )}
      </div>
    );
  }
}

export default withStore(Collections);
