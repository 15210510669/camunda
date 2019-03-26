pipelineJob('deploy-branch-to-k8s') {

  displayName 'Deploy branch to K8s'
  description 'Deploys branch to Kubernetes.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_k8s_branches.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('INFRASTRUCTURE_BRANCH', 'master', 'Infrastructure branch to use for checkout of deployment script.')
    stringParam('BRANCH', 'master', 'Operate branch to use for deployment.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }
}
