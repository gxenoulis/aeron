/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;

import static io.aeron.logbuffer.FrameDescriptor.*;

/**
 * A {@link FragmentHandler} that sits in a chain-of-responsibility pattern that reassembles fragmented messages
 * so that the next handler in the chain only sees whole messages. This is for a single session on an {@link Image}
 * and not for multiple session {@link Image}s in a {@link Subscription}.
 *
 * Unfragmented messages are delegated without copy. Fragmented messages are copied to a temporary
 * buffer for reassembly before delegation.
 *
 * The {@link Header} passed to the delegate on assembling a message will be that of the last fragment.
 */
public class ImageFragmentAssembler implements FragmentHandler
{
    private final FragmentHandler delegate;
    private final BufferBuilder builder;

    /**
     * Construct an adapter to reassemble message fragments and delegate on only whole messages.
     *
     * @param delegate onto which whole messages are forwarded.
     */
    public ImageFragmentAssembler(final FragmentHandler delegate)
    {
        this(delegate, BufferBuilder.INITIAL_CAPACITY);
    }

    /**
     * Construct an adapter to reassemble message fragments and delegate on only whole messages.
     *
     * @param delegate            onto which whole messages are forwarded.
     * @param initialBufferLength to be used for each session.
     */
    public ImageFragmentAssembler(final FragmentHandler delegate, final int initialBufferLength)
    {
        this.delegate = delegate;
        this.builder = new BufferBuilder(initialBufferLength);
    }

    /**
     * The implementation of {@link FragmentHandler} that reassembles and forwards whole messages.
     *
     * @param buffer containing the data.
     * @param offset at which the data begins.
     * @param length of the data in bytes.
     * @param header representing the meta data for the data.
     */
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final byte flags = header.flags();

        if ((flags & UNFRAGMENTED) == UNFRAGMENTED)
        {
            delegate.onFragment(buffer, offset, length, header);
        }
        else
        {
            handleFragment(buffer, offset, length, header, flags);
        }
    }

    private void handleFragment(
        final DirectBuffer buffer, final int offset, final int length, final Header header, final byte flags)
    {
        if ((flags & BEGIN_FRAG_FLAG) == BEGIN_FRAG_FLAG)
        {
            builder.reset().append(buffer, offset, length);
        }
        else
        {
            builder.append(buffer, offset, length);

            if ((flags & END_FRAG_FLAG) == END_FRAG_FLAG)
            {
                final int msgLength = builder.limit();
                delegate.onFragment(builder.buffer(), 0, msgLength, header);
                builder.reset();
            }
        }
    }
}