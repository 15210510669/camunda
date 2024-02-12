/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.stream.api.EventFilter;
import java.util.Set;

/** A filter that skips events with positions in a given set of positions. */
public final class SkipPositionsFilter implements EventFilter {

  private final Set<Long> positionsToSkip;

  private SkipPositionsFilter(final Set<Long> positionsToSkip) {

    this.positionsToSkip = positionsToSkip;
  }

  public static SkipPositionsFilter of(final Set<Long> positionsToSkip) {
    return new SkipPositionsFilter(positionsToSkip);
  }

  /**
   * @return true if the event position is not in the set of positions to skip
   */
  @Override
  public boolean applies(final LoggedEvent event) {
    return !positionsToSkip.contains(event.getPosition());
  }
}
