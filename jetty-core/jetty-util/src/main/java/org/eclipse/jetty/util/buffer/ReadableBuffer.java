//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.jetty.util.Retainable;
import org.eclipse.jetty.util.internal.AccumulatingReadBuffer;
import org.eclipse.jetty.util.internal.FixedSizeBuffer;

public interface ReadableBuffer extends Retainable
{
    ReadableBuffer EMPTY = new FixedSizeBuffer.ReadOnly(ByteBuffer.allocate(0).flip(), Retainable.NON_RETAINABLE);

    static ReadableBuffer wrap(ByteBuffer byteBuffer)
    {
        return new FixedSizeBuffer(byteBuffer, new ReferenceCounter(), false);
    }

    static ReadableBuffer accumulate(List<ReadableBuffer> readableBuffers)
    {
        if (readableBuffers.isEmpty())
            return EMPTY;
        return new AccumulatingReadBuffer(readableBuffers);
    }

    long position();

    void position(long newPosition);

    long capacity();

    long remaining();

    byte get();

    short getShort();

    int getInt();

    long getLong();

    ReadableBuffer slice();

    ReadableBuffer slice(long position, long length);

    WritableBuffer compact();

    WritableBuffer clear();

    WritableBuffer toWritable();

    long writeTo(Target target) throws IOException;

    interface Target
    {
        /**
         * @param byteBufferToWrite the buffer to be written
         */
        void write(ByteBuffer byteBufferToWrite) throws IOException;
    }
}
