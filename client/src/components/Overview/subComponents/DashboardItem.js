import React from 'react';
import {Button, Icon} from 'components';
import LastModified from './LastModified';
import {Link} from 'react-router-dom';
import entityIcons from '../entityIcons';
import CollectionsDropdown from './CollectionsDropdown';
import {formatters} from 'services';

const EntityIcon = entityIcons.dashboard.generic.Component;

export default function DashboardItem({
  dashboard,
  duplicateEntity,
  showDeleteModalFor,
  collection,
  searchQuery
}) {
  return (
    <li className="DashboardItem listItem">
      <Link className="info" to={`/dashboard/${dashboard.id}`}>
        <span className="icon">
          <EntityIcon />
        </span>
        <div className="textInfo">
          <div className="data dataTitle">
            <h3>{formatters.getHighlightedText(dashboard.name, searchQuery)}</h3>
          </div>
          <div className="extraInfo">
            <span className="data custom">
              {dashboard.reports.length} Report
              {dashboard.reports.length !== 1 ? 's' : ''}
            </span>
            <LastModified
              label="Last changed"
              date={dashboard.lastModified}
              author={dashboard.lastModifier}
            />
          </div>
        </div>
      </Link>
      <CollectionsDropdown entity={dashboard} currentCollection={collection} />
      <div className="operations">
        <Link title="Edit Dashboard" to={`/dashboard/${dashboard.id}/edit`}>
          <Icon title="Edit Dashboard" type="edit" className="editLink" />
        </Link>
        <Button
          title="Duplicate Dashboard"
          onClick={duplicateEntity('dashboard', dashboard, collection)}
        >
          <Icon type="copy-document" title="Duplicate Dashboard" className="duplicateIcon" />
        </Button>
        <Button
          title="Delete Dashboard"
          onClick={showDeleteModalFor({
            type: 'dashboard',
            entity: dashboard
          })}
        >
          <Icon type="delete" title="Delete Dashboard" className="deleteIcon" />
        </Button>
      </div>
    </li>
  );
}
