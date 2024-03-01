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
package io.camunda.operate.logging.stackdriver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * POJO allowing the easy construction and serialization of a Stackdriver compatible LogEntry
 *
 * <p>See here for documentation:
 * https://cloud.google.com/logging/docs/agent/configuration#special-fields
 * https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry
 */
@JsonInclude(Include.NON_EMPTY)
public final class StackdriverLogEntry {
  // Setting this as the entry's type will guarantee it will show up in the Error Reporting tool
  public static final String ERROR_REPORT_TYPE =
      "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

  @JsonProperty("severity")
  private String severity;

  @JsonProperty("logging.googleapis.com/sourceLocation")
  private SourceLocation sourceLocation;

  @JsonProperty(value = "message", required = true)
  private String message;

  @JsonProperty("serviceContext")
  private ServiceContext service;

  @JsonProperty("context")
  private Map<String, Object> context;

  @JsonProperty("@type")
  private String type;

  @JsonProperty("exception")
  private String exception;

  @JsonProperty("timestampSeconds")
  private Long timestampSeconds;

  @JsonProperty("timestampNanos")
  private Long timestampNanos;

  StackdriverLogEntry() {}

  public static StackdriverLogEntryBuilder builder() {
    return new StackdriverLogEntryBuilder();
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(final String severity) {
    this.severity = severity;
  }

  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }

  public void setSourceLocation(final SourceLocation sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public ServiceContext getService() {
    return service;
  }

  public void setService(final ServiceContext service) {
    this.service = service;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public void setContext(final Map<String, Object> context) {
    this.context = context;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getException() {
    return exception;
  }

  public void setException(final String exception) {
    this.exception = exception;
  }

  public long getTimestampSeconds() {
    return timestampSeconds;
  }

  public void setTimestampSeconds(final long timestampSeconds) {
    this.timestampSeconds = timestampSeconds;
  }

  public long getTimestampNanos() {
    return timestampNanos;
  }

  public void setTimestampNanos(final long timestampNanos) {
    this.timestampNanos = timestampNanos;
  }
}
