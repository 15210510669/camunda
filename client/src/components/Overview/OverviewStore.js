import React, {Component} from 'react';

import {Redirect} from 'react-router-dom';
import {checkDeleteConflict} from 'services';
import {withErrorHandling} from 'HOC';

import {load, remove, create, update} from './service';
import {getEntitiesCollections} from './service';
const OverviewContext = React.createContext();

class OverviewStore extends Component {
  state = {
    loading: true,
    redirect: false,
    deleting: false,
    collections: [],
    reports: [],
    dashboards: [],
    updating: null,
    conflicts: [],
    deleteLoading: false
  };

  async componentDidMount() {
    await this.loadData();
  }

  loadData = async () => {
    this.props.mightFail(
      await Promise.all([load('collection'), load('report'), load('dashboard')]),
      ([collections, reports, dashboards]) => {
        this.setState({collections, reports, dashboards, loading: false});
      }
    );
  };

  createCombinedReport = async () =>
    this.setState({
      redirect: '/report/' + (await create('report', null, {combined: true, reportType: 'process'}))
    });
  createProcessReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await create('report', null, {combined: false, reportType: 'process'}))
    });
  createDecisionReport = async () =>
    this.setState({
      redirect:
        '/report/' + (await create('report', null, {combined: false, reportType: 'decision'}))
    });

  createDashboard = async () =>
    this.setState({redirect: '/dashboard/' + (await create('dashboard'))});

  updateOrCreateCollection = async collection => {
    const editCollection = this.state.updating;
    if (editCollection.id) {
      await update('collection', editCollection.id, collection);
    } else {
      await create('collection', collection);
    }
    this.setState({updating: null});

    this.loadData();
  };

  deleteEntity = async () => {
    const {type, entity} = this.state.deleting;

    this.setState({deleteLoading: true});

    await remove(type, entity.id);

    this.setState({
      deleting: false,
      deleteLoading: false,
      conflicts: []
    });
    this.loadData();
  };

  duplicateEntity = (type, entity, collection) => async evt => {
    evt.target.blur();

    const copy = {
      ...entity,
      name: entity.name + ' - Copy'
    };

    let id;
    if (type === 'report') {
      id = await create(type, copy, {combined: copy.combined, reportType: copy.reportType});
    } else {
      id = await create(type, copy);
    }

    if (collection) {
      this.toggleEntityCollection({id}, collection, false)();
    } else {
      this.loadData();
    }
  };

  setCollectionToUpdate = updating => this.setState({updating});

  showDeleteModalFor = deleting => async () => {
    this.setState({deleting, deleteLoading: true});
    if (deleting.type !== 'collection') {
      const {conflictedItems} = await checkDeleteConflict(deleting.entity.id, deleting.type);
      this.setState({conflicts: conflictedItems});
    }
    this.setState({deleteLoading: false});
  };

  hideDeleteModal = () => this.setState({deleting: false, conflicts: []});

  toggleEntityCollection = (report, collection, isRemove) => async evt => {
    const collectionReportsIds = collection.data.entities.map(report => report.id);

    const change = {data: {}};
    if (isRemove) {
      change.data.entities = collectionReportsIds.filter(id => id !== report.id);
    } else {
      change.data.entities = [...collectionReportsIds, report.id];
    }

    await update('collection', collection.id, change);
    await this.loadData();
  };

  render() {
    const {redirect} = this.state;
    const entitiesCollections = getEntitiesCollections(this.state.collections);

    if (redirect) {
      return <Redirect to={`${redirect}/edit?new`} />;
    }

    const {
      createCombinedReport,
      createProcessReport,
      createDecisionReport,
      createDashboard,
      updateOrCreateCollection,
      deleteEntity,
      duplicateEntity,
      setCollectionToUpdate,
      showDeleteModalFor,
      hideDeleteModal,
      toggleEntityCollection,
      state
    } = this;

    const contextValue = {
      createCombinedReport,
      createProcessReport,
      createDecisionReport,
      createDashboard,
      updateOrCreateCollection,
      duplicateEntity,
      deleteEntity,
      showDeleteModalFor,
      hideDeleteModal,
      setCollectionToUpdate,
      toggleEntityCollection,
      store: state,
      entitiesCollections,
      error: this.props.error
    };

    return (
      <OverviewContext.Provider value={contextValue}>
        {this.props.children}
      </OverviewContext.Provider>
    );
  }
}

export const StoreProvider = withErrorHandling(OverviewStore);

export const withStore = Component => {
  function WithStore(props) {
    return (
      <OverviewContext.Consumer>
        {overviewProps => <Component {...props} {...overviewProps} />}
      </OverviewContext.Consumer>
    );
  }

  WithStore.WrappedComponent = Component;

  return WithStore;
};
