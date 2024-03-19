/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import java.io.IOException;
import java.util.*;
import org.springframework.lang.Nullable;

public interface ProcessStore {

  // General methods -> TODO: refactor to upper interface?
  Optional<Long> getDistinctCountFor(final String fieldName);

  void refreshIndices(String... indices);

  // ProcessStore
  ProcessEntity getProcessByKey(final Long processDefinitionKey);

  String getDiagramByKey(final Long processDefinitionKey);

  Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      String tenantId, @Nullable Set<String> allowedBPMNprocessIds);

  Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(
      @Nullable Set<String> allowedBPMNIds, int maxSize, String... fields);

  long deleteProcessDefinitionsByKeys(Long... processDefinitionKeys);

  /// Process instance methods
  ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey);

  Map<String, Long> getCoreStatistics(@Nullable Set<String> allowedBPMNids);

  String getProcessInstanceTreePathById(final String processInstanceId);

  List<Map<String, String>> createCallHierarchyFor(
      List<String> processInstanceIds, final String currentProcessInstanceId);

  long deleteDocument(final String indexName, final String idField, String id) throws IOException;

  void deleteProcessInstanceFromTreePath(String processInstanceKey);

  List<ProcessInstanceForListViewEntity> getProcessInstancesByProcessAndStates(
      long processDefinitionKey,
      Set<ProcessInstanceState> states,
      int size,
      String[] includeFields);

  List<ProcessInstanceForListViewEntity> getProcessInstancesByParentKeys(
      Set<Long> parentProcessInstanceKeys, int size, String[] includeFields);

  long deleteProcessInstancesAndDependants(Set<Long> processInstanceKeys);

  class ProcessKey {
    private String bpmnProcessId;
    private String tenantId;

    public ProcessKey(String bpmnProcessId, String tenantId) {
      this.bpmnProcessId = bpmnProcessId;
      this.tenantId = tenantId;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public ProcessKey setBpmnProcessId(String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public String getTenantId() {
      return tenantId;
    }

    public ProcessKey setTenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(bpmnProcessId, tenantId);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ProcessKey that = (ProcessKey) o;
      return Objects.equals(bpmnProcessId, that.bpmnProcessId)
          && Objects.equals(tenantId, that.tenantId);
    }
  }
}
