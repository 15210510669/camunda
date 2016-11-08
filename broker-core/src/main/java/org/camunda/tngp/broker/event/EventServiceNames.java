/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.event;

import org.camunda.tngp.servicecontainer.ServiceName;

public class EventServiceNames
{
    public static final ServiceName<EventManager> EVENT_MANAGER_SERVICE = ServiceName.newServiceName("event.manager", EventManager.class);
    public static final ServiceName<EventContext> EVENT_CONTEXT_SERVICE_GROUP_NAME = ServiceName.newServiceName("event.contexts", EventContext.class);
    public static final ServiceName<EventContext> EVENT_CONTEXT_SERVICE = ServiceName.newServiceName("event.context", EventContext.class);

}
