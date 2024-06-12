/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import java.util.List;
import java.util.Optional;

public interface CollectionReader {

  Optional<CollectionDefinitionDto> getCollection(String collectionId);

  List<CollectionDefinitionDto> getAllCollections();
}
