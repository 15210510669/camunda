/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import com.google.common.collect.ImmutableMap;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.eventName;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.group;
import static org.camunda.optimize.dto.optimize.query.event.EventCountDto.Fields.source;

@NoArgsConstructor
public class EventCountSorter extends Sorter<EventCountDto> {

  private static final Comparator<EventCountDto> SUGGESTED_COMPARATOR =
    Comparator.comparing(EventCountDto::isSuggested, nullsFirst(naturalOrder())).reversed();
  private static final Comparator<EventCountDto> GROUP_COMPARATOR =
    Comparator.comparing(EventCountDto::getGroup, nullsFirst(String.CASE_INSENSITIVE_ORDER));
  private static final Comparator<EventCountDto> SOURCE_COMPARATOR =
    Comparator.comparing(EventCountDto::getSource, nullsFirst(String.CASE_INSENSITIVE_ORDER));
  private static final Comparator<EventCountDto> EVENT_NAME_COMPARATOR =
    Comparator.comparing(eventCountDto -> Optional.ofNullable(eventCountDto.getEventLabel())
      .orElse(eventCountDto.getEventName()), nullsFirst(String.CASE_INSENSITIVE_ORDER));

  private static final Comparator<EventCountDto> DEFAULT_COMPARATOR = nullsFirst(
    GROUP_COMPARATOR.thenComparing(SOURCE_COMPARATOR).thenComparing(EVENT_NAME_COMPARATOR));

  private static final ImmutableMap<String, Comparator<EventCountDto>> sortComparators = ImmutableMap.of(
    group.toLowerCase(), GROUP_COMPARATOR,
    source.toLowerCase(), SOURCE_COMPARATOR,
    eventName.toLowerCase(), EVENT_NAME_COMPARATOR
  );

  @Override
  public List<EventCountDto> applySort(List<EventCountDto> eventCounts) {
    Comparator<EventCountDto> eventCountSorter;
    if (sortBy != null) {
      if (!sortComparators.containsKey(sortBy.toLowerCase())) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      }
      eventCountSorter = sortComparators.get(sortBy.toLowerCase())
        .thenComparing(DEFAULT_COMPARATOR);
      if (SortOrder.DESC.equals(sortOrder)) {
        eventCountSorter = eventCountSorter.reversed();
      }
    } else {
      if (sortOrder != null) {
        throw new BadRequestException("Sort order is not supported when no field selected to sort");
      }
      eventCountSorter = DEFAULT_COMPARATOR;
    }
    eventCounts.sort(SUGGESTED_COMPARATOR.thenComparing(eventCountSorter));
    return eventCounts;
  }
}
