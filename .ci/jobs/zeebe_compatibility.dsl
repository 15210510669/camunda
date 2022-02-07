pipelineJob('zeebe_compatibility') {

  displayName 'Zeebe Integration Tests with supported Zeebe Versions'
  description 'Runs Zeebe ITs with different supported Zeebe versions to check compatibility.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/zeebe_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for Zeebe ITs.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 23 * * 1-5')
        }
      }
    }
  }
}
