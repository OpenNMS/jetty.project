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

import org.eclipse.jetty.util.Retainable;
import org.eclipse.jetty.util.internal.FixedSizeBuffer;

public interface WritableBuffer extends Retainable
{
    WritableBuffer EMPTY = new FixedSizeBuffer.WriteOnly(ByteBuffer.allocate(0), Retainable.NON_RETAINABLE);

    static WritableBuffer wrap(ByteBuffer byteBuffer)
    {
        return new FixedSizeBuffer(byteBuffer, new ReferenceCounter(), true);
    }

    static WritableBuffer wrap(ByteBuffer byteBuffer, Retainable retainable)
    {
        return new FixedSizeBuffer(byteBuffer, retainable, true);
    }

    static WritableBuffer allocate(int size, boolean direct)
    {
        return new FixedSizeBuffer(direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size), new ReferenceCounter(), true);
    }

    long position();

    void position(long newPosition);

    long capacity();

    long remaining();

    long availableForRead();

    void put(byte b);

    void put(ReadableBuffer readableBuffer);

    void putShort(short s);

    void putInt(int i);

    void putLong(long l);

    ReadableBuffer toReadable();

    long readFrom(Fount fount) throws IOException;

    interface Fount
    {
        /**
         * @param byteBufferToReadInto the buffer to read into
         * @return true if EOF was reached while reading, false otherwise
         */
        boolean read(ByteBuffer byteBufferToReadInto) throws IOException;
    }
}
