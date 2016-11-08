package org.camunda.tngp.broker.services;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class DeferredResponsePoolService implements Service<DeferredResponsePool>
{
    protected Injector<Dispatcher> sendBufferInector = new Injector<>();
    protected final int poolCapacity;

    protected DeferredResponsePool responsePool;

    public DeferredResponsePoolService(int poolCapacity)
    {
        this.poolCapacity = poolCapacity;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        responsePool = new DeferredResponsePool(sendBufferInector.getValue(), poolCapacity);
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        // nothing to do
    }

    @Override
    public DeferredResponsePool get()
    {
        return responsePool;
    }

    public Injector<Dispatcher> getSendBufferInector()
    {
        return sendBufferInector;
    }

}
