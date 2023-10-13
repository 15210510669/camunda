/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import java.io.IOException;
import java.util.List;

public interface TestSearchRepository {
  <R> List<R> searchAll(String index, Class<R> clazz) throws IOException;
}
