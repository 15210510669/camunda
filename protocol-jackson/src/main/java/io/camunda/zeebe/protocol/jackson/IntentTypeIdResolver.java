/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import io.camunda.zeebe.protocol.record.ValueTypeMapping.Mapping;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Objects;

/**
 * A {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} that maps a serialized {@link
 * Intent} value to a concrete implementation enum, e.g. {@link
 * io.camunda.zeebe.protocol.record.intent.VariableIntent}, based on the value type of the record.
 */
final class IntentTypeIdResolver extends AbstractValueTypeIdResolver {
  @Override
  protected Class<? extends Intent> mapFromValueType(final ValueType valueType) {
    final Mapping<?, ?> typeInfo =
        ValueTypeMapping.get(Objects.requireNonNull(valueType, "must specify a value type"));
    return typeInfo.getIntentClass();
  }
}
