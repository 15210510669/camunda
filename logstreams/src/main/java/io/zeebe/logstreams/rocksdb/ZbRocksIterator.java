/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.rocksdb;

import io.zeebe.util.EnsureUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

public class ZbRocksIterator extends RocksIterator {
  private static final Method SEEK_METHOD;

  static {
    try {
      SEEK_METHOD =
          RocksIterator.class.getDeclaredMethod("seek0", long.class, byte[].class, int.class);
      SEEK_METHOD.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private transient DirectBuffer keyBuffer, valueBuffer;

  public ZbRocksIterator(final RocksDB rocksDB, final long nativeHandle) {
    super(rocksDB, nativeHandle);
  }

  public DirectBuffer keyBuffer() {
    if (keyBuffer == null) {
      keyBuffer = new UnsafeBuffer(super.key());
    }

    return keyBuffer;
  }

  public DirectBuffer valueBuffer() {
    if (valueBuffer == null) {
      valueBuffer = new UnsafeBuffer(super.value());
    }

    return valueBuffer;
  }

  public ZbRocksEntry entry() {
    return new ZbRocksEntry(keyBuffer(), valueBuffer());
  }

  public void seek(byte[] target, int targetLength) {
    try {
      SEEK_METHOD.invoke(this, nativeHandle_, target, targetLength);
      reset();
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public void seek(DirectBuffer target) {
    assertBuffers(target);
    seek(target.byteArray(), target.capacity());
  }

  @Override
  public void seek(byte[] target) {
    super.seek(target);
    reset();
  }

  @Override
  public void seekToFirst() {
    super.seekToFirst();
    reset();
  }

  @Override
  public void seekToLast() {
    super.seekToLast();
    reset();
  }

  @Override
  public void seekForPrev(byte[] target) {
    super.seekForPrev(target);
    reset();
  }

  @Override
  public void next() {
    super.next();
    reset();
  }

  @Override
  public void prev() {
    super.prev();
    reset();
  }

  private void reset() {
    keyBuffer = valueBuffer = null;
  }

  private void assertBuffers(final DirectBuffer... buffers) {
    for (final DirectBuffer buffer : buffers) {
      EnsureUtil.ensureArrayBacked(buffer);
      assert buffer.wrapAdjustment() == 0 : "only supports reading from offset 0";
    }
  }
}
