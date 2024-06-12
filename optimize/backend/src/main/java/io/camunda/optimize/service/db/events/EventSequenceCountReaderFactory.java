/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.events;

import io.camunda.optimize.service.db.reader.EventSequenceCountReader;

public interface EventSequenceCountReaderFactory {

  EventSequenceCountReader createEventSequenceCountReader(final String eventSuffix);
}
