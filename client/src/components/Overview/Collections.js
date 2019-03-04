import React from 'react';
import {Button, Icon} from 'components';
import {Link} from 'react-router-dom';
import classnames from 'classnames';

import entityIcons from './entityIcons';
import {getReportInfo, getReportIcon} from './service';
import LastModified from './subComponents/LastModified';
import UpdateCollectionModal from './subComponents/UpdateCollectionModal';

import './Collections.scss';

const HeaderIcon = entityIcons.collection.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Collections extends React.Component {
  state = {
    limit: true
  };

  renderReport = (itemData, idx) => {
    const {Icon: ReportIcon, label} = getReportIcon(itemData);

    return (
      <li className="item" key={idx}>
        <Link className="info" to={`/report/${itemData.id}`}>
          <span className="icon" title={label}>
            <ReportIcon />
          </span>
          <div className="textInfo">
            <div className="data dataTitle">
              <h3>{itemData.name}</h3>
              {itemData.combined && <span>Combined</span>}
              {itemData.reportType && itemData.reportType === 'decision' && <span>Decision</span>}
            </div>
            <div className="extraInfo">
              <span className="data custom">{getReportInfo(itemData)}</span>
              <LastModified date={itemData.lastModified} author={itemData.lastModifier} />
            </div>
          </div>
        </Link>
        <div className="operations">
          <Link title="Edit Report" to={`/report/${itemData.id}/edit`}>
            <Icon title="Edit Report" type="edit" className="editLink" />
          </Link>
          <Button title="Duplicate Report" onClick={this.props.duplicateReport(itemData)}>
            <Icon type="copy-document" title="Duplicate Report" className="duplicateIcon" />
          </Button>
          <Button
            title="Delete Report"
            onClick={this.props.showDeleteModalFor({type: 'reports', entity: itemData})}
          >
            <Icon type="delete" title="Delete Report" className="deleteIcon" />
          </Button>
        </div>
      </li>
    );
  };

  renderCollection = ({id, name, created, owner, data}, idx) => {
    const reports = data ? data.entities : [];

    return (
      <li key={idx} className="collection">
        <div className="item">
          <div className="info">
            <span className="icon">
              <HeaderIcon />
            </span>
            <Button
              className="ToggleCollapse"
              onClick={() => this.setState({[id]: !this.state[id]})}
            >
              <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state[id]})} />

              <div className="textInfo">
                <div className="dataTitle">
                  <h2>{name}</h2>
                </div>
                <div className="extraInfo">
                  <div className="custom">
                    <span>{reports.length} Items</span>
                  </div>
                  <LastModified label="Created" date={created} author={owner} />
                </div>
              </div>
            </Button>
          </div>
          <div className="operations">
            <Button
              title="Edit Collection"
              onClick={() => this.props.setCollectionToUpdate({id, name})}
            >
              <Icon title="Edit Collection" type="edit" className="editLink" />
            </Button>
            <Button
              title="Delete Report"
              onClick={this.props.showDeleteModalFor({type: 'collections', entity: {id, name}})}
            >
              <Icon type="delete" title="Delete Report" className="deleteIcon" />
            </Button>
          </div>
        </div>
        {this.state[id] && (
          <>
            {reports.length > 0 ? (
              <ul className="entityList">
                {reports.slice(0, this.state.limit ? 5 : undefined).map(this.renderReport)}
              </ul>
            ) : (
              <p className="emptyCollection">There are no items in this Collection.</p>
            )}
            {!this.state.loading &&
              reports.length > 5 &&
              (this.state.limit ? (
                <>
                  {reports.length} Reports.{' '}
                  <Button type="link" onClick={() => this.setState({limit: false})}>
                    Show all...
                  </Button>
                </>
              ) : (
                <Button type="link" onClick={() => this.setState({limit: true})}>
                  Show less...
                </Button>
              ))}
          </>
        )}
      </li>
    );
  };

  render() {
    const empty = this.props.collections.length === 0 && (
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

    return (
      <div className="Collections">
        <ul className="entityList">
          {empty}
          {this.props.collections.length > 0 && this.props.collections.map(this.renderCollection)}
        </ul>
        {this.props.updating && (
          <UpdateCollectionModal
            collection={this.props.updating}
            onClose={() => this.props.setCollectionToUpdate(null)}
            onConfirm={this.props.updateOrCreateCollection}
          />
        )}
      </div>
    );
  }
}

export default Collections;
