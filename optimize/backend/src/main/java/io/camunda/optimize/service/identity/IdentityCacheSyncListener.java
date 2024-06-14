/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.identity;

import io.camunda.optimize.service.SearchableIdentityCache;

public interface IdentityCacheSyncListener {

  void onFinishIdentitySync(SearchableIdentityCache newIdentityCache);
}
