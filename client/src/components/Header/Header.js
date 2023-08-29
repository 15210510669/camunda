/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Link, useLocation} from 'react-router-dom';
import {C3Navigation, C3UserConfigurationProvider} from '@camunda/camunda-composite-components';

import {Tooltip, NavItem} from 'components';
import {
  getOptimizeProfile,
  isEnterpriseMode,
  getWebappLinks,
  getOnboardingConfig,
  getNotificationsUrl,
} from 'config';
import {withDocs, withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {track} from 'tracking';

import {isEventBasedProcessEnabled, getUserToken} from './service';
import WhatsNewModal from './WhatsNewModal';
import {TelemetrySettings} from './TelemetrySettings';
import useUserMenu from './useUserMenu';

import './Header.scss';

export function Header({user, mightFail, docsLink, noActions}) {
  const [showEventBased, setShowEventBased] = useState(false);
  const [enterpriseMode, setEnterpiseMode] = useState(true);
  const [webappLinks, setwebappLinks] = useState(null);
  const [whatsNewOpen, setWhatsNewOpen] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);
  const location = useLocation();
  const [organizationId, setOrganizationId] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [userToken, setUserToken] = useState(null);
  const [notificationsUrl, setNotificationsUrl] = useState();
  const userSideBar = useUserMenu({user, mightFail, setTelemetrySettingsOpen});

  useEffect(() => {
    mightFail(
      Promise.all([
        isEventBasedProcessEnabled(),
        getOptimizeProfile(),
        isEnterpriseMode(),
        getWebappLinks(),
        getOnboardingConfig(),
        getUserToken(),
        getNotificationsUrl(),
      ]),
      ([
        enabled,
        optimizeProfile,
        isEnterpriseMode,
        webappLinks,
        onboardingConfig,
        userToken,
        notificationsUrl,
      ]) => {
        setShowEventBased(enabled && optimizeProfile === 'platform');
        setEnterpiseMode(isEnterpriseMode);
        setwebappLinks(webappLinks);
        setOptimizeProfile(optimizeProfile);
        setOrganizationId(onboardingConfig.orgId);
        setUserToken(userToken);
        setNotificationsUrl(notificationsUrl);
      },
      showError
    );
  }, [mightFail]);

  const props = {
    app: createAppProps(location),
    appBar: createAppBarProps(webappLinks),
    navbar: {elements: []},
  };

  if (!noActions) {
    props.navbar = createNavBarProps(showEventBased, enterpriseMode);
    props.infoSideBar = createInfoSideBarProps(setWhatsNewOpen, docsLink, enterpriseMode);
    props.userSideBar = userSideBar;
  }

  const isCloud = optimizeProfile === 'cloud';
  if (isCloud) {
    props.notificationSideBar = {
      key: 'notifications',
      isOpen: false,
      ariaLabel: 'Notifications',
    };
  }

  return (
    <C3UserConfigurationProvider
      {...getNavigationConfiguration(
        isCloud,
        organizationId,
        userToken,
        getUserToken,
        notificationsUrl
      )}
    >
      <C3Navigation {...props} />
      <WhatsNewModal open={whatsNewOpen} onClose={() => setWhatsNewOpen(false)} />
      {telemetrySettingsOpen && (
        <TelemetrySettings onClose={() => setTelemetrySettingsOpen(false)} />
      )}
    </C3UserConfigurationProvider>
  );
}

function createAppProps(location) {
  return {
    name: t('appName'),
    ariaLabel: t('appFullName'),
    routeProps: {
      as: Link,
      className: 'cds--header__name appLink',
      to: '/',
      replace: location.pathname === '/',
    },
  };
}

function createAppBarProps(webappLinks) {
  return {
    type: 'app',
    ariaLabel: t('navigation.appSwitcher'),
    isOpen: false,
    elements: createWebappLinks(webappLinks),
    elementClicked: (app) => {
      track(app + ':open');
    },
  };
}

function createWebappLinks(webappLinks) {
  if (!webappLinks) {
    return [];
  }

  return Object.entries(webappLinks).map(([key, href]) => ({
    key,
    label: t(`navigation.apps.${key}`),
    ariaLabel: t(`navigation.apps.${key}`),
    href,
    active: key === 'optimize',
    routeProps: key === 'optimize' ? {to: '/'} : undefined,
  }));
}

function createNavBarProps(showEventBased, enterpriseMode) {
  const elements = [
    {
      key: 'dashboards',
      label: t('navigation.dashboards'),
      routeProps: {
        as: NavItem,
        name: t('navigation.dashboards'),
        linksTo: '/processes',
        active: ['/processes/', '/processes/*'],
        breadcrumbsEntities: [{entity: 'report'}],
      },
    },
    {
      key: 'collections',
      label: t('navigation.collections'),
      routeProps: {
        as: NavItem,
        name: t('navigation.collections'),
        linksTo: '/',
        active: ['/', '/report/*', '/dashboard/*', '/collection/*'],
        breadcrumbsEntities: [{entity: 'collection'}, {entity: 'dashboard'}, {entity: 'report'}],
      },
    },
    {
      key: 'analysis',
      label: t('navigation.analysis'),
      routeProps: {
        as: NavItem,
        name: t('navigation.analysis'),
        linksTo: '/analysis',
        active: ['/analysis/', '/analysis/*'],
      },
    },
  ];

  if (showEventBased) {
    elements.push({
      key: 'events',
      label: t('navigation.events'),
      routeProps: {
        as: NavItem,
        name: t('navigation.events'),
        linksTo: '/events/processes/',
        active: ['/events/processes/', '/events/ingested/', '/events/processes/*'],
        breadcrumbsEntities: [{entity: 'eventBasedProcess', entityUrl: 'events/processes'}],
      },
    });
  }

  const tags = [];
  if (!enterpriseMode) {
    tags.push({
      key: 'licenseWarning',
      label: (
        <Tooltip
          content={
            <>
              {t('license.referTo')}{' '}
              <a
                href="https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/"
                target="_blank"
                rel="noopener noreferrer"
              >
                {t('license.terms')}
              </a>{' '}
              {t('common.or')}{' '}
              <a href="https://camunda.com/contact/" target="_blank" rel="noopener noreferrer">
                {t('license.contactSales')}
              </a>
            </>
          }
          delay={500}
        >
          <div className="warning">{t('license.nonProduction')}</div>
        </Tooltip>
      ),
      color: 'red',
    });
  }

  return {
    elements,
    tags,
  };
}

function createInfoSideBarProps(setWhatsNewOpen, docsLink, enterpriseMode) {
  return {
    type: 'info',
    ariaLabel: 'Info',
    elements: [
      {
        key: 'whatsNew',
        label: t('whatsNew.buttonTitle'),
        onClick: () => {
          setWhatsNewOpen(true);
        },
      },
      {
        key: 'userguide',
        label: t('navigation.userGuide'),
        onClick: () => {
          window.open(docsLink + 'components/what-is-optimize/', '_blank', 'noopener,noreferrer');
        },
      },
      {
        key: 'academy',
        label: t('navigation.academy'),
        onClick: () => {
          window.open('https://academy.camunda.com/', '_blank');
        },
      },
      {
        key: 'feedbackAndSupport',
        label: t('navigation.feedback'),
        onClick: () => {
          if (enterpriseMode) {
            window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
          } else {
            window.open('https://forum.camunda.io/', '_blank');
          }
        },
      },
      {
        key: 'slackCommunityChannel',
        label: t('navigation.slack'),
        onClick: () => {
          window.open('https://camunda-slack-invite.herokuapp.com/', '_blank');
        },
      },
    ],
  };
}

function getNavigationConfiguration(
  isCloud,
  activeOrganizationId,
  userToken,
  getNewUserToken,
  notificationsUrl
) {
  return isCloud && userToken && notificationsUrl
    ? {
        endpoints: {notifications: notificationsUrl},
        userToken,
        getNewUserToken,
        activeOrganizationId,
      }
    : {};
}

export default withUser(withDocs(withErrorHandling(Header)));
