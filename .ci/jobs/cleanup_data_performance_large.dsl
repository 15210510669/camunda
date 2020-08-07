pipelineJob('cleanup-data-performance') {

  displayName 'History Cleanup Performance test'
  description 'Test Optimize History Cleanup Performance against a large static dataset.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/cleanup_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-medium.sqlc', 'optimize_data-large.sqlc', 'optimize_data-stage.sqlc'])
    stringParam('ES_REFRESH_INTERVAL', '5s', 'Elasticsearch index refresh interval.')
    stringParam('ES_NUM_NODES', '1', 'Number of Elasticsearch nodes in the cluster (not more than 5)')
    stringParam('CLEANUP_TIMEOUT_MINUTES', '120', 'Time limit for a cleanup run to finish')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 5 * * *')
        }
      }
    }
  }
}
