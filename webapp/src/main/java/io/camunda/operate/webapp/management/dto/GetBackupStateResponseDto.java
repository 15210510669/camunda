/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import java.util.List;
import java.util.Objects;

public class GetBackupStateResponseDto {

  private Long backupId;
  private BackupStateDto state;
  private String failureReason;
  private List<GetBackupStateResponseDetailDto> details;

  public GetBackupStateResponseDto() {
  }

  public GetBackupStateResponseDto(Long backupId) {
    this.backupId = backupId;
  }

  public Long getBackupId() {
    return backupId;
  }

  public GetBackupStateResponseDto setBackupId(Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public BackupStateDto getState() {
    return state;
  }

  public GetBackupStateResponseDto setState(BackupStateDto state) {
    this.state = state;
    return this;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public GetBackupStateResponseDto setFailureReason(String failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public List<GetBackupStateResponseDetailDto> getDetails() {
    return details;
  }

  public GetBackupStateResponseDto setDetails(List<GetBackupStateResponseDetailDto> details) {
    this.details = details;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GetBackupStateResponseDto that = (GetBackupStateResponseDto) o;
    return Objects.equals(backupId, that.backupId) && state == that.state && Objects.equals(failureReason,
        that.failureReason) && Objects.equals(details, that.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(backupId, state, failureReason, details);
  }
}
