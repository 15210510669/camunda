/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class UserTaskBuilderTest {

  @Test
  void testUserTaskFormIdNotNull() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1")
            .zeebeUserTaskForm("{}")
            .endEvent()
            .done();

    final Collection<ZeebeUserTaskForm> zeebeUserTaskForms =
        instance.getModelElementsByType(ZeebeUserTaskForm.class);

    assertThat(zeebeUserTaskForms).hasSize(1);
    final ZeebeUserTaskForm zeebeUserTaskForm = zeebeUserTaskForms.iterator().next();
    assertThat(zeebeUserTaskForm.getId()).isNotNull();
  }
}
