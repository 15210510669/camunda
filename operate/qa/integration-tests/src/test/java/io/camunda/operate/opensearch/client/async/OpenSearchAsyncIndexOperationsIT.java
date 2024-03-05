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
package io.camunda.operate.opensearch.client.async;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import java.util.List;
import org.junit.Test;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchAsyncIndexOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired UserIndex userIndex;

  @Autowired OperateProperties operateProperties;

  @Test
  public void shouldReindex() throws Exception {
    // given
    String id = "1";
    opensearchTestDataHelper.addUser(id, "test", "test");

    // when
    String dstIndex = indexPrefix + this.getClass().getSimpleName().toLowerCase();
    Query query = stringTerms("userId", List.of(id));
    var deleteByQueryRequestBuilder =
        reindexRequestBuilder(userIndex.getFullQualifiedName(), query, dstIndex)
            .waitForCompletion(false)
            .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .slices((long) operateProperties.getOpensearch().getNumberOfShards())
            .conflicts(Conflicts.Proceed)
            .refresh(true);

    var task =
        richOpenSearchClient
            .async()
            .index()
            .reindex(deleteByQueryRequestBuilder, Throwable::getMessage)
            .get()
            .task();

    // then
    var total =
        withThreadPoolTaskScheduler(
            scheduler -> {
              try {
                return richOpenSearchClient
                    .async()
                    .task()
                    .totalImpactedByTask(task, scheduler)
                    .get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(total).isEqualTo(1);

    var user =
        richOpenSearchClient
            .doc()
            .searchUnique(searchRequestBuilder(dstIndex).query(query), UserEntity.class, id);
    assertThat(user.getDisplayName()).isEqualTo("test");
  }
}
