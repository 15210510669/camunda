package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.broker.system.threads.AgentRunnerService;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;

public class AsyncRequestWorkerService implements Service<AsyncRequestWorker>
{
    protected final Injector<AgentRunnerService> agentRunnerInjector = new Injector<>();
    protected final Injector<AsyncRequestWorkerContext> workerContextInjector = new Injector<>();

    protected AsyncRequestWorker worker;

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final AsyncRequestWorkerContext workerContext = workerContextInjector.getValue();
        final AgentRunnerService agentRunnerService = agentRunnerInjector.getValue();

        worker = createWorker(serviceContext.getName(), workerContext);

        agentRunnerService.runWorkerAgent(worker);
    }

    protected AsyncRequestWorker createWorker(String name, final AsyncRequestWorkerContext workerContext)
    {
        return new AsyncRequestWorker(name, workerContext);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        agentRunnerInjector.getValue().removeWorkerAgent(worker);
    }

    @Override
    public AsyncRequestWorker get()
    {
        return null;
    }

    public Injector<AgentRunnerService> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

    public Injector<AsyncRequestWorkerContext> getWorkerContextInjector()
    {
        return workerContextInjector;
    }
}
