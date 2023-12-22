/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.azure.manifest.Manifest;
import io.camunda.zeebe.backup.azure.manifest.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.azure.manifest.Manifest.StatusCode;
import io.camunda.zeebe.backup.azure.shared.BlobRequestsOptionsManagement;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class ManifestManager {
  public static final int PRECONDITION_FAILED = 412;

  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code "manifests"}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code nodeId}
   *   <li>{@code "manifest.json"}
   * </ul>
   *
   * The path format is constructed by partitionId/checkpointId/nodeId/manifest.json
   */
  private static final String MANIFEST_PATH_FORMAT = "%s/%s/%s/manifest.json";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  private boolean containerCreated = false;
  private final BlobContainerClient blobContainerClient;

  ManifestManager(final BlobContainerClient blobContainerClient) {
    this.blobContainerClient = blobContainerClient;
  }

  InProgressManifest createInitialManifest(final Backup backup) {

    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;
    assureContainerCreated();
    try {
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath((manifest)));

    try {
      final BlobRequestConditions blobRequestConditions =
          BlobRequestsOptionsManagement.acquireBlobLease(blobClient);
      BlobRequestsOptionsManagement.disableOverwrite(blobRequestConditions);
      blobClient.uploadWithResponse(
          new BlobParallelUploadOptions(BinaryData.fromBytes(serializedManifest))
              .setRequestConditions(blobRequestConditions),
          null,
          Context.NONE);
      BlobRequestsOptionsManagement.releaseLease(blobClient);
      return manifest;
    } catch (final BlobStorageException e) {
      if (e.getStatusCode() == PRECONDITION_FAILED
          || e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
        // if resource is locked throws PRECONDITION_FAILED, so must already exist and in use.
        // upload() can also throw BlobStorageException with BLOB_ALREADY_EXISTS,
        // since upload does not overwrite by default, both are unexpected states.
        BlobRequestsOptionsManagement.releaseLease(blobClient);
        throw new UnexpectedManifestState(e.getMessage());
      }
      throw new RuntimeException(e);
    }
  }

  void completeManifest(final InProgressManifest inProgressManifest) {
    final byte[] serializedManifest;
    final var completed = inProgressManifest.complete();
    assureContainerCreated();
    try {
      serializedManifest = MAPPER.writeValueAsBytes(completed);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(completed));
    try {

      final BlobRequestConditions blobRequestConditions =
          BlobRequestsOptionsManagement.acquireBlobLease(blobClient);
      final Manifest manifest = getManifest(inProgressManifest.id());

      if (manifest == null) {
        BlobRequestsOptionsManagement.releaseLease(blobClient);
        throw new UnexpectedManifestState("Manifest does not exist.");
      } else if (manifest.statusCode() != StatusCode.IN_PROGRESS) {
        BlobRequestsOptionsManagement.releaseLease(blobClient);
        throw new UnexpectedManifestState(
            "Expected manifest to be in progress but was in %s"
                .formatted(manifest.statusCode().name()));
      }

      // uploadWithResponse() will overwrite by default
      blobClient.uploadWithResponse(
          new BlobParallelUploadOptions(BinaryData.fromBytes(serializedManifest))
              .setRequestConditions(blobRequestConditions),
          null,
          Context.NONE);
      BlobRequestsOptionsManagement.releaseLease(blobClient);
    } catch (final BlobStorageException e) {
      BlobRequestsOptionsManagement.releaseLease(blobClient);
      throw new RuntimeException(e);
    }
  }

  void markAsFailed(final Manifest existingManifest, final String failureReason) {
    assureContainerCreated();
    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(existingManifest));
    final var updatedManifest =
        switch (existingManifest.statusCode()) {
          case FAILED -> existingManifest.asFailed();
          case COMPLETED -> existingManifest.asCompleted().fail(failureReason);
          case IN_PROGRESS -> existingManifest.asInProgress().fail(failureReason);
        };

    if (existingManifest != updatedManifest) {
      try {
        blobClient.upload(BinaryData.fromBytes(MAPPER.writeValueAsBytes(updatedManifest)), true);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    assureContainerCreated();
    final BlobClient blobClient =
        blobContainerClient.getBlobClient(
            MANIFEST_PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId()));
    if (!blobClient.exists()) {
      return null;
    }
    final BinaryData binaryData = blobClient.downloadContent();

    try {
      return MAPPER.readValue(binaryData.toStream(), Manifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String manifestPath(final Manifest manifest) {
    return MANIFEST_PATH_FORMAT.formatted(
        manifest.id().partitionId(), manifest.id().checkpointId(), manifest.id().nodeId());
  }

  void assureContainerCreated() {
    if (!containerCreated) {
      blobContainerClient.createIfNotExists();
      containerCreated = true;
    }
  }
}
