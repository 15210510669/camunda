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
package io.camunda.tasklist.schema.indices;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import io.camunda.tasklist.schema.backup.Prio3Backup;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VariableIndex extends AbstractIndexDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "variable";
  public static final String INDEX_VERSION = "8.3.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String SCOPE_FLOW_NODE_ID = "scopeFlowNodeId";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String FULL_VALUE = "fullValue";
  public static final String IS_PREVIEW = "isPreview";
  public static final String TENANT_ID = "tenantId";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getAllIndicesPattern() {
    return getFullQualifiedName();
  }

  private static Optional<String> getElsFieldByGraphqlField(String fieldName) {
    switch (fieldName) {
      case ("id"):
        return of(ID);
      case ("name"):
        return of(NAME);
      case ("value"):
        return of(FULL_VALUE);
      case ("previewValue"):
        return of(VALUE);
      case ("isValueTruncated"):
        return of(IS_PREVIEW);
      default:
        return empty();
    }
  }

  public static Set<String> getElsFieldsByGraphqlFields(Set<String> fieldNames) {
    return fieldNames.stream()
        .map((fn) -> getElsFieldByGraphqlField(fn))
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
