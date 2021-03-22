/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.migration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.zeebe.tasklist.schema.SemanticVersion;
import java.time.OffsetDateTime;
import java.util.Comparator;

/**
 * A step describes a change in one index in a specific version and in which order inside the
 * version.<br>
 * A step stores when it was created and applied.<br>
 * The change is described in content of step.<br>
 * It also provides comparators for SemanticVersion and order comparing.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({@JsonSubTypes.Type(value = ProcessorStep.class)})
public interface Step {

  Comparator<Step> SEMANTICVERSION_COMPARATOR =
      new Comparator<Step>() {
        @Override
        public int compare(Step s1, Step s2) {
          return SemanticVersion.fromVersion(s1.getVersion())
              .compareTo(SemanticVersion.fromVersion(s2.getVersion()));
        }
      };

  Comparator<Step> ORDER_COMPARATOR =
      new Comparator<Step>() {
        @Override
        public int compare(Step s1, Step s2) {
          return s1.getOrder().compareTo(s2.getOrder());
        }
      };

  Comparator<Step> SEMANTICVERSION_ORDER_COMPARATOR =
      new Comparator<Step>() {
        @Override
        public int compare(Step s1, Step s2) {
          int result = SEMANTICVERSION_COMPARATOR.compare(s1, s2);
          if (result == 0) {
            result = ORDER_COMPARATOR.compare(s1, s2);
          }
          return result;
        }
      };

  String INDEX_NAME = "indexName",
      CREATED_DATE = "createdDate",
      APPLIED = "applied",
      APPLIED_DATE = "appliedDate",
      VERSION = "version",
      ORDER = "order",
      CONTENT = "content";

  public OffsetDateTime getCreatedDate();

  public Step setCreatedDate(final OffsetDateTime date);

  public OffsetDateTime getAppliedDate();

  public Step setAppliedDate(final OffsetDateTime date);

  public String getVersion();

  public Integer getOrder();

  public boolean isApplied();

  public Step setApplied(final boolean isApplied);

  public String getIndexName();

  public String getContent();

  public String getDescription();
}
