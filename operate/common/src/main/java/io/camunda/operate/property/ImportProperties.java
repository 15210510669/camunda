/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.property;

public class ImportProperties {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;
  public static final int DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL = 10000;
  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;
  private static final int DEFAULT_POST_IMPORT_THREADS_COUNT = 1;
  private static final int DEFAULT_READER_THREADS_COUNT = 3;
  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 3;
  private static final int DEFAULT_READER_BACKOFF = 5000;
  private static final int DEFAULT_SCHEDULER_BACKOFF = 5000;
  private static final int DEFAULT_FLOW_NODE_TREE_CACHE_SIZE = 1000;
  private static final int DEFAULT_MAX_EMPTY_RUNS = 10;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int postImportThreadsCount = DEFAULT_POST_IMPORT_THREADS_COUNT;

  // is here for testing purposes
  private boolean postImportEnabled = true;

  private boolean postImporterIgnoreMissingData = false;

  // used for cases where sequences are not valid
  private boolean useOnlyPosition = false;

  private int readerThreadsCount = DEFAULT_READER_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  private int readerBackoff = DEFAULT_READER_BACKOFF;

  /**
   * The property is not used anymore. Instead of a backoff, the records reader gets rescheduled
   * once the queue has capacity.
   */
  @Deprecated(since = "8.1.0")
  private int schedulerBackoff = DEFAULT_SCHEDULER_BACKOFF;

  private int flowNodeTreeCacheSize = DEFAULT_FLOW_NODE_TREE_CACHE_SIZE;

  private int importPositionUpdateInterval = DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL;

  /** Indicates, whether loading of Zeebe data should start on startup. */
  private boolean startLoadingDataOnStartup = true;

  /** Variable size under which we won't store preview separately. */
  private int variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  /**
   * When we build hierarchies for flow node instances (e.g. subprocess -> task inside subprocess)
   * and for process instances parent instance -> child instance), we normally read data only from
   * runtime indices. But it may occur that data was partially archived already. In this case import
   * process will be stuck with errors "Unable to find parent tree path for flow node instance" or
   * "Unable to find parent tree path for parent instance". This parameter allows to read parent
   * instances from archived indices. Should not be set true forever for performance reasons.
   */
  private boolean readArchivedParents = false;

  private int maxEmptyRuns = DEFAULT_MAX_EMPTY_RUNS;

  public boolean isStartLoadingDataOnStartup() {
    return startLoadingDataOnStartup;
  }

  public void setStartLoadingDataOnStartup(boolean startLoadingDataOnStartup) {
    this.startLoadingDataOnStartup = startLoadingDataOnStartup;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public int getPostImportThreadsCount() {
    return postImportThreadsCount;
  }

  public ImportProperties setPostImportThreadsCount(final int postImportThreadsCount) {
    this.postImportThreadsCount = postImportThreadsCount;
    return this;
  }

  public boolean isPostImportEnabled() {
    return postImportEnabled;
  }

  public ImportProperties setPostImportEnabled(boolean postImportEnabled) {
    this.postImportEnabled = postImportEnabled;
    return this;
  }

  public boolean isPostImporterIgnoreMissingData() {
    return postImporterIgnoreMissingData;
  }

  public ImportProperties setPostImporterIgnoreMissingData(boolean postImporterIgnoreMissingData) {
    this.postImporterIgnoreMissingData = postImporterIgnoreMissingData;
    return this;
  }

  public boolean isUseOnlyPosition() {
    return useOnlyPosition;
  }

  public ImportProperties setUseOnlyPosition(boolean useOnlyPosition) {
    this.useOnlyPosition = useOnlyPosition;
    return this;
  }

  public int getReaderThreadsCount() {
    return readerThreadsCount;
  }

  public ImportProperties setReaderThreadsCount(final int readerThreadsCount) {
    this.readerThreadsCount = readerThreadsCount;
    return this;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }

  public int getReaderBackoff() {
    return readerBackoff;
  }

  public void setReaderBackoff(int readerBackoff) {
    this.readerBackoff = readerBackoff;
  }

  public int getSchedulerBackoff() {
    return schedulerBackoff;
  }

  public void setSchedulerBackoff(int schedulerBackoff) {
    this.schedulerBackoff = schedulerBackoff;
  }

  public int getFlowNodeTreeCacheSize() {
    return flowNodeTreeCacheSize;
  }

  public void setFlowNodeTreeCacheSize(final int flowNodeTreeCacheSize) {
    this.flowNodeTreeCacheSize = flowNodeTreeCacheSize;
  }

  public int getVariableSizeThreshold() {
    return variableSizeThreshold;
  }

  public ImportProperties setVariableSizeThreshold(final int variableSizeThreshold) {
    this.variableSizeThreshold = variableSizeThreshold;
    return this;
  }

  public int getImportPositionUpdateInterval() {
    return importPositionUpdateInterval;
  }

  public void setImportPositionUpdateInterval(int importPositionUpdateInterval) {
    this.importPositionUpdateInterval = importPositionUpdateInterval;
  }

  public boolean isReadArchivedParents() {
    return readArchivedParents;
  }

  public ImportProperties setReadArchivedParents(boolean readArchivedParents) {
    this.readArchivedParents = readArchivedParents;
    return this;
  }

  public int getMaxEmptyRuns() {
    return maxEmptyRuns;
  }

  public ImportProperties setMaxEmptyRuns(int maxEmptyRuns) {
    this.maxEmptyRuns = maxEmptyRuns;
    return this;
  }
}
