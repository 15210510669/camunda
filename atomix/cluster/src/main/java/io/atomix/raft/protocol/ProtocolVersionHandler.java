/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

public class ProtocolVersionHandler {

  private static final int VERSION_APPENDREQUEST = 1;
  private static final int VERSION_APPENDREQUEST_V2 = 2;

  public static InternalAppendRequest transform(final AppendRequest request) {
    return new InternalAppendRequest(
        VERSION_APPENDREQUEST,
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogIndex(),
        request.commitIndex(),
        request.entries());
  }

  public static InternalAppendRequest transform(final VersionedAppendRequest request) {
    return new InternalAppendRequest(
        VERSION_APPENDREQUEST_V2,
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogIndex(),
        request.commitIndex(),
        request.entries());
  }
}
