/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionWriter {
  public static final String DEFAULT_COLLECTION_NAME = "New Collection";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter formatter;

  public IdDto createNewCollectionAndReturnId(@NonNull String userId,
                                              @NonNull PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    log.debug("Writing new collection to Elasticsearch");

    String id = IdGenerator.getNextId();
    SimpleCollectionDefinitionDto simpleCollectionDefinitionDto = new SimpleCollectionDefinitionDto();
    simpleCollectionDefinitionDto.setId(id);
    simpleCollectionDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    simpleCollectionDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    simpleCollectionDefinitionDto.setOwner(userId);
    simpleCollectionDefinitionDto.setLastModifier(userId);
    simpleCollectionDefinitionDto.setName(Optional.ofNullable(partialCollectionDefinitionDto.getName())
                                            .orElse(DEFAULT_COLLECTION_NAME));

    final CollectionDataDto newCollectionDataDto = new CollectionDataDto();
    newCollectionDataDto.getRoles().add(new CollectionRoleDto(new UserDto(userId), RoleType.MANAGER));
    if (partialCollectionDefinitionDto.getData() != null) {
      newCollectionDataDto.setConfiguration(partialCollectionDefinitionDto.getData().getConfiguration());
    }
    simpleCollectionDefinitionDto.setData(newCollectionDataDto);

    persistCollection(id, simpleCollectionDefinitionDto);
    return new IdDto(id);
  }

  private void persistCollection(String id, SimpleCollectionDefinitionDto simpleCollectionDefinitionDto) {
    try {
      IndexRequest request = new IndexRequest(COLLECTION_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(simpleCollectionDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write collection to Elasticsearch. ";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create collection.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Collection with id [{}] has successfully been created.", id);
  }

  public void createNewCollection(@NonNull SimpleCollectionDefinitionDto simpleCollectionDefinitionDto) {
    persistCollection(simpleCollectionDefinitionDto.getId(), simpleCollectionDefinitionDto);
  }

  public void updateCollection(CollectionDefinitionUpdateDto collection, String id) {
    log.debug("Updating collection with id [{}] in Elasticsearch", id);

    try {
      UpdateRequest request =
        new UpdateRequest()
          .index(COLLECTION_INDEX_NAME)
          .id(id)
          .doc(objectMapper.writeValueAsString(collection), XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error(
          "Was not able to update collection with id [{}] and name [{}].",
          id,
          collection.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s].",
        id,
        collection.getName()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s]. Collection does not exist!",
        id,
        collection.getName()
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void deleteCollection(String collectionId) {
    log.debug("Deleting collection with id [{}]", collectionId);
    DeleteRequest request = new DeleteRequest(COLLECTION_INDEX_NAME)
      .id(collectionId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete collection with id [%s]. ", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message = String.format("Could not delete collection with id [%s]. Collection does not exist." +
                                       "Maybe it was already deleted by someone else?", collectionId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  public void addScopeEntriesToCollection(final String userId,
                                          final String collectionId,
                                          final List<CollectionScopeEntryDto> scopeUpdates) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put(
        "scopeEntriesToUpdate",
        objectMapper.convertValue(scopeUpdates, new TypeReference<List<Map>>() {
        })
      );
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));
      final Script updateEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        "Map newScopes = ctx._source.data.scope.stream()" +
          "  .collect(Collectors.toMap(s -> s.id, Function.identity()));\n" +
          "params.scopeEntriesToUpdate" +
          "  .forEach(newScope -> {" +
          "     newScopes.computeIfPresent(newScope.id, (key, oldScope) -> {" +
          "       newScope.tenants = Stream.concat(oldScope.tenants.stream(), newScope.tenants.stream())" +
          "        .distinct()" +
          "        .collect(Collectors.toList());\n" +
          "       return newScope;\n" +
          "     });" +
          "     newScopes.putIfAbsent(newScope.id, newScope);\n" +
          "  });\n" +
          "ctx._source.data.scope = newScopes.values();" +
          "ctx._source.lastModifier = params.lastModifier;" +
          "ctx._source.lastModified = params.lastModified;",
        params
      );

      final UpdateResponse updateResponse;
      updateResponse = executeUpdateRequest(
        collectionId,
        updateEntityScript,
        "Was not able to update collection with id [%s]."
      );


      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOT_FOUND)) {
        final String message = String.format(
          "Was not able to add scope entries to collection with id [%s]. Collection does not exist!",
          collectionId
        );
        log.error(message);
        throw new NotFoundException(message);
      }
    } catch (IOException e) {
      String errorMessage = String.format("Wasn't able to add scope entries to collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void removeScopeEntry(String collectionId, String scopeEntryId, String userId) throws NotFoundException {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("id", scopeEntryId);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script updateEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        "boolean removed = ctx._source.data.scope.removeIf(scope -> scope.id.equals(params.id));" +
          "if (removed) { " +
          "  ctx._source.lastModifier = params.lastModifier;" +
          "  ctx._source.lastModified = params.lastModified;" +
          "} else {" +
          "  ctx.op = \"none\";" +
          "}",
        params
      );

      UpdateResponse updateResponse = executeUpdateRequest(
        collectionId,
        updateEntityScript,
        "Was not able to update collection with id [%s]."
      );

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message = String.format("Scope entry for id [%s] doesn't exists.", scopeEntryId);
        log.warn(message);
        throw new NotFoundException(message);
      }

    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateScopeEntity(String collectionId,
                                CollectionScopeEntryUpdateDto scopeEntry,
                                String userId,
                                String scopeEntryId) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("entryDto", objectMapper.convertValue(scopeEntry, Object.class));
      params.put("entryId", scopeEntryId);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script updateEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        "def optionalEntry = ctx._source.data.scope.stream()" +
          "  .filter(s -> s.id.equals(params.entryId))" +
          "  .findFirst();" +
          "if (optionalEntry.isPresent()) {" +
          "  def entry = optionalEntry.get();" +
          "  entry.tenants = params.entryDto.tenants;" +
          "  entry.versions = params.entryDto.versions;" +
          "  ctx._source.lastModifier = params.lastModifier;" +
          "  ctx._source.lastModified = params.lastModified;" +
          "} else { " +
          "  throw new Exception('Cannot find scope entry.');" +
          "}",
        params
      );

      executeUpdateRequest(
        collectionId, updateEntityScript, "Was not able to update collection with id [%s]."
      );

    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update scope entry with id [%s] on collection with id [%s]." +
          " Collection or scope Entry does not exist!",
        scopeEntryId,
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private UpdateResponse executeUpdateRequest(String collectionId, Script updateEntityScript, String errorMessage)
    throws IOException {
    final UpdateRequest request = new UpdateRequest()
      .index(COLLECTION_INDEX_NAME)
      .id(collectionId)
      .script(updateEntityScript)
      .setRefreshPolicy(IMMEDIATE)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

    if (updateResponse.getShardInfo().getFailed() > 0) {
      final String message = String.format(errorMessage, collectionId);
      log.error(message, collectionId);
      throw new OptimizeRuntimeException(message);
    }
    return updateResponse;
  }

  public CollectionRoleDto addRoleToCollection(String collectionId, CollectionRoleDto roleDto, String userId)
    throws OptimizeCollectionConflictException {
    log.debug("Adding role [{}] to collection with id [{}] in Elasticsearch.", roleDto.getId(), collectionId);

    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("roleDto", objectMapper.convertValue(roleDto, Object.class));
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script addEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        // @formatter:off
        "boolean exists = ctx._source.data.roles.stream()" +
          ".filter(dto -> dto.id.equals(params.roleDto.id))" +
          ".findFirst().isPresent();" +
        "if(!exists){ " +
          "ctx._source.data.roles.add(params.roleDto); " +
          "ctx._source.lastModifier = params.lastModifier; " +
          "ctx._source.lastModified = params.lastModified; " +
        "} else {" +
          // ES is inconsistent on the op value, for update queries it's 'none'
          // see https://github.com/elastic/elasticsearch/issues/30356
          "ctx.op = \"none\";" +
        "}",
        // @formatter:on
        params
      );

      final UpdateResponse updateResponse = executeUpdateRequest(
        collectionId,
        addEntityScript,
        "Was not able to update collection with id [%s]."
      );

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message = String.format("Role resource for id [%s] already exists.", roleDto.getId());
        log.warn(message);
        throw new OptimizeCollectionConflictException(message);
      }

      return roleDto;
    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s]. Collection does not exist!",
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void updateRoleInCollection(final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto,
                                     final String userId) throws OptimizeConflictException {
    log.debug("Updating the role [{}] in collection with id [{}] in Elasticsearch.", roleEntryId, collectionId);

    try {
      final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
      params.put("role", roleUpdateDto.getRole().toString());

      final Script addEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        // @formatter:off
        "def optionalExistingEntry = ctx._source.data.roles.stream()" +
          ".filter(dto -> dto.id.equals(params.roleEntryId))" +
          ".findFirst();" +
        "if(optionalExistingEntry.isPresent()){ " +
          "def existingEntry = optionalExistingEntry.get();" +
          "def moreThanOneManagerPresent = ctx._source.data.roles.stream()" +
            ".filter(dto -> params.managerRole.equals(dto.role))" +
            ".limit(2)" +
            ".count()" +
            "== 2;" +
          "if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {" +
            // updating of last manager is not allowed
            "ctx.op = \"none\";" +
          "} else {" +
            "existingEntry.role = params.role;" +
            "ctx._source.lastModifier = params.lastModifier; " +
            "ctx._source.lastModified = params.lastModified; " +
          "}" +
        "} else {" +
          "throw new Exception('Cannot find role.');" +
        "}",
        // @formatter:on
        params
      );

      final UpdateResponse updateResponse = executeUpdateRequest(
        collectionId,
        addEntityScript,
        "Was not able to update collection with id [%s]."
      );

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message = String.format(
          "Cannot assign lower privileged role to last [%s] of collection [%s].",
          RoleType.MANAGER,
          collectionId
        );
        log.warn(message);
        throw new OptimizeConflictException(message);
      }
    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
        roleEntryId,
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                          final String userId)
    throws OptimizeConflictException {
    final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
    removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, params);
  }

  public void removeRoleFromCollection(final String collectionId, final String roleEntryId) {
    final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, null);
    removeRoleFromCollection(collectionId, roleEntryId, params);
  }

  private void removeRoleFromCollection(final String collectionId, final String roleEntryId,
                                        final Map<String, Object> params) {
    log.debug("Deleting the role [{}] in collection with id [{}] in Elasticsearch.", roleEntryId, collectionId);
    try {
      final Script addEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        // @formatter:off
        "def optionalExistingEntry = ctx._source.data.roles.stream()" +
          ".filter(dto -> dto.id.equals(params.roleEntryId))" +
          ".findFirst();" +
        "if(optionalExistingEntry.isPresent()){ " +
          "def existingEntry = optionalExistingEntry.get();" +
          "ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));" +
          "if (params.containsKey(\"lastModifier\")) {" +
            "ctx._source.lastModifier = params.lastModifier;" +
          "}" +
          "if (params.containsKey(\"lastModified\")) {" +
            "ctx._source.lastModified = params.lastModified;" +
          "}" +
        "} else {" +
          "throw new Exception('Cannot find role.');" +
        "}",
        // @formatter:on
        params
      );

      executeUpdateRequest(
        collectionId,
        addEntityScript,
        "Was not able to delete role from collection with id [%s]."
      );

    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
        roleEntryId,
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                           final Map<String, Object> params)
    throws OptimizeConflictException {
    log.debug("Deleting the role [{}] in collection with id [{}] in Elasticsearch.", roleEntryId, collectionId);
    try {
      final Script addEntityScript = ElasticsearchWriterUtil.createDefaultScript(
        // @formatter:off
        "def optionalExistingEntry = ctx._source.data.roles.stream()" +
          ".filter(dto -> dto.id.equals(params.roleEntryId))" +
          ".findFirst();" +
        "if(optionalExistingEntry.isPresent()){ " +
          "def existingEntry = optionalExistingEntry.get();" +
          "def moreThanOneManagerPresent = ctx._source.data.roles.stream()" +
            ".filter(dto -> params.managerRole.equals(dto.role))" +
            ".limit(2)" +
            ".count()" +
            "== 2;" +
          "if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {" +
            // deletion of last manager is not allowed
            "ctx.op = \"none\";" +
          "} else {" +
            "ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));" +
            "if (params.containsKey(\"lastModifier\")) {" +
              "ctx._source.lastModifier = params.lastModifier;" +
            "}" +
            "if (params.containsKey(\"lastModified\")) {" +
              "ctx._source.lastModified = params.lastModified;" +
            "}" +
          "}" +
        "} else {" +
          "throw new Exception('Cannot find role.');" +
        "}",
        // @formatter:on
        params
      );

      final UpdateResponse updateResponse = executeUpdateRequest(
        collectionId,
        addEntityScript,
        "Was not able to delete role from collection with id [%s]."
      );

      if (updateResponse.getResult() == DocWriteResponse.Result.NOOP) {
        final String message = String.format(
          "Cannot delete last [%s] of collection [%s].", RoleType.MANAGER, collectionId
        );
        log.warn(message);
        throw new OptimizeConflictException(message);
      }
    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
        roleEntryId,
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private Map<String, Object> constructParamsForRoleUpdateScript(String roleEntryId, String userId) {
    final Map<String, Object> params = new HashMap<>();
    params.put("roleEntryId", roleEntryId);
    params.put("managerRole", RoleType.MANAGER.toString());
    if (userId != null) {
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));
    }
    return params;
  }
}
