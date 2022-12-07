## PR Checklist

- [ ] This PR is targeting the correct branch (see details below):
  <details>
  <summary>If it's a fix during a regular release</summary>
  This PR should target only the release branch (as this branch will be later merged into master)
  </details>
  <details>
  <summary>If it's a fix for a PATCH release</summary>
  This PR should target the maintenance branch AND you have to make sure it is manually cherry-picked to the master branch as well, since the maintenance branch DOES NOT get merged back to master.
  </details>
  <details>
  <summary>Otherwise (normal task)</summary>
  This PR should target the master branch
  </details>
- [ ] I have added testing notes to the JIRA ticket
- [ ] If there are API changes, I have updated our [Rest API docs](https://confluence.camunda.com/display/CO/REST-API)
- [ ] If there are schema changes, this PR includes a migration script or a link to a follow-up task for the migration script
