/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Column, Grid, Link} from '@carbon/react';
import CheckImage from 'modules/images/orange-check-mark.svg';
import {getStateLocally} from 'modules/utils/localStorage';
import {Restricted} from 'modules/components/Restricted';
import {useTasks} from 'modules/queries/useTasks';
import styles from './styles.module.scss';

const EmptyPage: React.FC = () => {
  const {isLoading: isLoadingTasks, data} = useTasks();
  const tasks = data?.pages.flat() ?? [];
  const hasNoTasks = tasks.length === 0;
  const isOldUser = getStateLocally('hasCompletedTask') === true;

  if (isLoadingTasks) {
    return <span data-testid="loading-state" />;
  }

  if (hasNoTasks && isOldUser) {
    return null;
  }

  return (
    <Grid className={styles.container} condensed>
      <Column
        className={styles.imageContainer}
        sm={1}
        md={{
          span: 2,
          offset: 1,
        }}
        lg={{
          span: 2,
          offset: 4,
        }}
        xlg={{
          span: 1,
          offset: 5,
        }}
      >
        <img className={styles.image} src={CheckImage} alt="" />
      </Column>
      <Column
        className={isOldUser ? styles.oldUserText : styles.newUserText}
        sm={3}
        md={5}
        lg={10}
        xlg={10}
      >
        {isOldUser ? (
          <Restricted
            fallback={<h3>Pick a task to view details</h3>}
            scopes={['write']}
          >
            <h3>Pick a task to work on</h3>
          </Restricted>
        ) : (
          <>
            <h3>Welcome to Tasklist</h3>
            <p data-testid="first-paragraph">
              Here you can perform user tasks you specify
              <br />
              through your BPMN diagram and forms.
            </p>
            {!hasNoTasks && <p>Select a task to view its details.</p>}
            <p data-testid="tutorial-paragraph">
              Follow our tutorial to{' '}
              <Link
                href="https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks"
                target="_blank"
                rel="noreferrer"
                inline
              >
                learn how to create tasks.
              </Link>
            </p>
          </>
        )}
      </Column>
    </Grid>
  );
};

EmptyPage.displayName = 'EmptyPage';

export {EmptyPage as Component};
