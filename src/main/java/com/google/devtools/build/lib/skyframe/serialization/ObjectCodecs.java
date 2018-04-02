// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.skyframe.serialization;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;

/**
 * Wrapper for the minutiae of serializing and deserializing objects using {@link ObjectCodec}s,
 * serving as a layer between the streaming-oriented {@link ObjectCodec} interface and users.
 */
public class ObjectCodecs {
  private final SerializationContext serializationContext;
  private final DeserializationContext deserializationContext;

  /**
   * Creates an instance using the supplied {@link ObjectCodecRegistry} for looking up {@link
   * ObjectCodec}s.
   */
  public ObjectCodecs(
      ObjectCodecRegistry codecRegistry, ImmutableMap<Class<?>, Object> dependencies) {
    serializationContext = new SerializationContext(codecRegistry, dependencies);
    deserializationContext = new DeserializationContext(codecRegistry, dependencies);
  }

  public ByteString serialize(Object subject) throws SerializationException {
    return serializeToByteString(subject, this::serialize);
  }

  public void serialize(Object subject, CodedOutputStream codedOut) throws SerializationException {
    serializeImpl(subject, codedOut, /*memoize=*/ false);
  }

  public ByteString serializeMemoized(Object subject) throws SerializationException {
    return serializeToByteString(subject, this::serializeMemoized);
  }

  public void serializeMemoized(Object subject, CodedOutputStream codedOut)
      throws SerializationException {
    serializeImpl(subject, codedOut, /*memoize=*/ true);
  }

  public Object deserialize(ByteString data) throws SerializationException {
    return deserialize(data.newCodedInput());
  }

  public Object deserialize(CodedInputStream codedIn) throws SerializationException {
    return deserializeImpl(codedIn, /*memoize=*/ false);
  }

  public Object deserializeMemoized(ByteString data) throws SerializationException {
    return deserializeMemoized(data.newCodedInput());
  }

  public Object deserializeMemoized(CodedInputStream codedIn) throws SerializationException {
    return deserializeImpl(codedIn, /*memoize=*/ true);
  }

  private void serializeImpl(Object subject, CodedOutputStream codedOut, boolean memoize)
      throws SerializationException {
    try {
      if (memoize) {
        serializationContext.getMemoizingContext().serialize(subject, codedOut);
      } else {
        serializationContext.serialize(subject, codedOut);
      }
    } catch (IOException e) {
      throw new SerializationException("Failed to serialize " + subject, e);
    }
  }

  private Object deserializeImpl(CodedInputStream codedIn, boolean memoize)
      throws SerializationException {
    // Allows access to buffer without copying (although this means buffer may be pinned in memory).
    codedIn.enableAliasing(true);
    try {
      if (memoize) {
        return deserializationContext.getMemoizingContext().deserialize(codedIn);
      } else {
        return deserializationContext.deserialize(codedIn);
      }
    } catch (IOException e) {
      throw new SerializationException("Failed to deserialize data", e);
    }
  }

  @FunctionalInterface
  private static interface SerializeCall {
    void serialize(Object subject, CodedOutputStream codedOut) throws SerializationException;
  }

  private static ByteString serializeToByteString(Object subject, SerializeCall wrapped)
      throws SerializationException {
    ByteString.Output resultOut = ByteString.newOutput();
    CodedOutputStream codedOut = CodedOutputStream.newInstance(resultOut);
    wrapped.serialize(subject, codedOut);
    try {
      codedOut.flush();
      return resultOut.toByteString();
    } catch (IOException e) {
      throw new SerializationException("Failed to serialize " + subject, e);
    }
  }
}
