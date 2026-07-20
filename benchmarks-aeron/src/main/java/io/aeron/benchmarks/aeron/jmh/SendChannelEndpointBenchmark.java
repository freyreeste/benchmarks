/*
 * Copyright 2015-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.benchmarks.aeron.jmh;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.media.SendChannelEndpoint;
import io.aeron.driver.media.UdpChannel;
import io.aeron.driver.status.SendChannelStatus;
import io.aeron.driver.status.SendLocalSocketAddress;
import org.agrona.IoUtil;
import org.agrona.concurrent.status.AtomicCounter;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;

public class SendChannelEndpointBenchmark
{
    private static final int MESSAGE_LENGTH = 32;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        MediaDriver.Context context;
        AtomicCounter statusIndicator;
        AtomicCounter localSocketAddressIndicator;
        SendChannelEndpoint sendChannelEndpoint;

        @Setup
        public void setup()
        {
            context = new MediaDriver.Context().dirDeleteOnStart(true);
            context.concludeAeronDirectory();

            if (context.aeronDirectory().isDirectory())
            {
                context.deleteAeronDirectory();
            }
            IoUtil.ensureDirectoryExists(context.aeronDirectory(), "aeron directory");
            context.conclude();

            final UdpChannel udpChannel = UdpChannel.parse("aeron:udp?endpoint=127.0.0.1:9999");
            final long registrationId = 1L;

            statusIndicator = SendChannelStatus.allocate(
                context.tempBuffer(), context.countersManager(), registrationId, udpChannel.originalUriString());

            sendChannelEndpoint = new SendChannelEndpoint(udpChannel, statusIndicator, context);

            localSocketAddressIndicator = SendLocalSocketAddress.allocate(
                context.tempBuffer(),
                context.countersManager(),
                registrationId,
                sendChannelEndpoint.statusIndicatorCounterId());

            sendChannelEndpoint.localSocketAddressIndicator(localSocketAddressIndicator);
            sendChannelEndpoint.openChannel();
            sendChannelEndpoint.indicateActive();
        }

        @TearDown
        public void tearDown()
        {
            sendChannelEndpoint.close();
            context.close();
            context.deleteAeronDirectory();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(MESSAGE_LENGTH);
        SendChannelEndpoint sendChannelEndpoint;

        @Setup
        public void setup(final SharedState sharedState)
        {
            sendChannelEndpoint = sharedState.sendChannelEndpoint;
        }
    }

    @Benchmark
    @BenchmarkMode({ Mode.SampleTime, Mode.AverageTime })
    @Threads(1)
    public int send(final PerThreadState state)
    {
        final ByteBuffer buffer = state.buffer;
        buffer.clear();

        return state.sendChannelEndpoint.send(buffer);
    }
}
