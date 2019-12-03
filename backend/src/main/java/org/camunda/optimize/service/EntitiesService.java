/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.camunda.optimize.service.security.AuthorizedEntitiesService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
public class EntitiesService {

  private CollectionService collectionService;
  private AuthorizedEntitiesService authorizedEntitiesService;
  private EntitiesReader entitiesReader;

  public List<EntityDto> getAllEntities(final String userId) {
    final List<AuthorizedCollectionDefinitionDto> collectionDefinitions =
      collectionService.getAllCollectionDefinitions(userId);
    final Map<String, Map<EntityType, Long>> collectionEntityCounts = entitiesReader.countEntitiesForCollections(
      collectionDefinitions.stream()
        .map(AuthorizedCollectionDefinitionDto::getDefinitionDto)
        .collect(Collectors.toList())
    );
    final List<EntityDto> privateEntities = authorizedEntitiesService.getAuthorizedPrivateEntities(userId);

    return Stream.concat(
      collectionDefinitions.stream()
        .map(AuthorizedCollectionDefinitionDto::toEntityDto)
        .peek(entityDto -> entityDto.getData().setSubEntityCounts(collectionEntityCounts.get(entityDto.getId()))),
      privateEntities.stream())
      .sorted(
        Comparator.comparing(EntityDto::getEntityType)
          .thenComparing(EntityDto::getLastModified, Comparator.reverseOrder())
      ).collect(Collectors.toList());
  }

  public EntityNameDto getEntityNames(final EntityNameRequestDto requestDto) {
    return entitiesReader.getEntityNames(requestDto);
  }

}
