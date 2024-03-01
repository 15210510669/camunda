/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.management.dto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class GetBackupStateResponseDetailDto {

  private String snapshotName;
  private String state;
  private OffsetDateTime startTime;
  private String[] failures;

  public GetBackupStateResponseDetailDto() {}

  public String getSnapshotName() {
    return snapshotName;
  }

  public GetBackupStateResponseDetailDto setSnapshotName(String snapshotName) {
    this.snapshotName = snapshotName;
    return this;
  }

  public String getState() {
    return state;
  }

  public GetBackupStateResponseDetailDto setState(String state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public GetBackupStateResponseDetailDto setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public String[] getFailures() {
    return failures;
  }

  public GetBackupStateResponseDetailDto setFailures(String[] failures) {
    this.failures = failures;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(snapshotName, state, startTime);
    result = 31 * result + Arrays.hashCode(failures);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GetBackupStateResponseDetailDto that = (GetBackupStateResponseDetailDto) o;
    return Objects.equals(snapshotName, that.snapshotName)
        && Objects.equals(state, that.state)
        && Objects.equals(startTime, that.startTime)
        && Arrays.equals(failures, that.failures);
  }
}
