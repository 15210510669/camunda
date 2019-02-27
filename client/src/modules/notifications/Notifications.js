import React from 'react';
import update from 'immutability-helper';
import {getRandomId} from 'services';

import Notification from './Notification';

import './Notifications.scss';

let notificationsInstance;

export default class Notifications extends React.Component {
  constructor(props) {
    super(props);

    notificationsInstance = this;

    this.state = {
      notifications: []
    };
  }

  addNotification = config => {
    this.setState(state => update(state, {notifications: {$push: [config]}}));
  };

  removeNotification = notificationToDelete => {
    this.setState(state => ({
      notifications: state.notifications.filter(
        notification => notification !== notificationToDelete
      )
    }));
  };

  render() {
    return (
      <div className="Notifications">
        {this.state.notifications.map(config => (
          <Notification
            config={config}
            remove={() => this.removeNotification(config)}
            key={config.id}
          />
        ))}
      </div>
    );
  }
}

export function addNotification(config) {
  if (typeof config === 'string') {
    config = {text: config};
  }
  notificationsInstance.addNotification({...config, id: getRandomId()});
}
