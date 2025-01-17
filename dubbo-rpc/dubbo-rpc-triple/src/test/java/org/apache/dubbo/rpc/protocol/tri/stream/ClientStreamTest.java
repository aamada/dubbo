/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.rpc.protocol.tri.stream;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.api.Connection;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.model.ModuleServiceRepository;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.protocol.tri.DefaultFuture2;
import org.apache.dubbo.rpc.protocol.tri.RequestMetadata;
import org.apache.dubbo.rpc.protocol.tri.RpcStatus;
import org.apache.dubbo.rpc.protocol.tri.TripleHeaderEnum;
import org.apache.dubbo.rpc.protocol.tri.command.CancelQueueCommand;
import org.apache.dubbo.rpc.protocol.tri.command.DataQueueCommand;
import org.apache.dubbo.rpc.protocol.tri.command.EndStreamQueueCommand;
import org.apache.dubbo.rpc.protocol.tri.command.HeaderQueueCommand;
import org.apache.dubbo.rpc.protocol.tri.command.QueuedCommand;
import org.apache.dubbo.rpc.protocol.tri.compressor.Identity;
import org.apache.dubbo.rpc.protocol.tri.pack.GenericPack;
import org.apache.dubbo.rpc.protocol.tri.pack.GenericUnpack;
import org.apache.dubbo.rpc.protocol.tri.support.IGreeter;
import org.apache.dubbo.rpc.protocol.tri.transport.H2TransportListener;
import org.apache.dubbo.rpc.protocol.tri.transport.WriteQueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientStreamTest {
    @Test
    public void progress() throws InterruptedException {
        final URL url = URL.valueOf("tri://127.0.0.1:8080/foo.bar.service");
        final Connection connection = mock(Connection.class);

        final ModuleServiceRepository repo = ApplicationModel.defaultModel().getDefaultModule().getServiceRepository();
        repo.registerService(IGreeter.class);
        final ServiceDescriptor serviceDescriptor = repo.getService(IGreeter.class.getName());
        final MethodDescriptor methodDescriptor = serviceDescriptor.getMethod("echo", new Class<?>[]{String.class});

        final RpcInvocation invocation = mock(RpcInvocation.class);
        int timeout = 1000;
        DefaultFuture2 future = DefaultFuture2.newFuture(connection, invocation, timeout, ImmediateEventExecutor.INSTANCE);
        MockClientStreamListener listener = new MockClientStreamListener();
        WriteQueue writeQueue = mock(WriteQueue.class);
        final EmbeddedChannel channel = new EmbeddedChannel();
        when(writeQueue.enqueue(any())).thenReturn(channel.newPromise());
        ClientStream stream = new ClientStream(url.getOrDefaultFrameworkModel(), future.requestId, ImmediateEventExecutor.INSTANCE, writeQueue, listener);

        final RequestMetadata requestMetadata = StreamUtils.createRequest(url, methodDescriptor,
            invocation, future.requestId, Identity.IDENTITY,
            "identity", timeout, mock(GenericPack.class), mock(GenericUnpack.class));
        stream.startCall(requestMetadata);
        verify(writeQueue).enqueue(any(HeaderQueueCommand.class));
        // no other commands
        verify(writeQueue).enqueue(any(QueuedCommand.class));
        stream.writeMessage(new byte[0], 0);
        verify(writeQueue).enqueue(any(DataQueueCommand.class));
        verify(writeQueue, times(2)).enqueue(any(QueuedCommand.class));
        stream.halfClose();
        verify(writeQueue).enqueue(any(EndStreamQueueCommand.class));
        verify(writeQueue, times(3)).enqueue(any(QueuedCommand.class));

        stream.cancelByLocal(RpcStatus.CANCELLED);
        verify(writeQueue).enqueue(any(CancelQueueCommand.class));
        verify(writeQueue, times(4)).enqueue(any(QueuedCommand.class));

        H2TransportListener transportListener = stream.createTransportListener();
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name())
            .status(HttpResponseStatus.OK.codeAsText());
        headers.set(TripleHeaderEnum.STATUS_KEY.getHeader(), RpcStatus.OK.code.code + "");
        headers.set(TripleHeaderEnum.CONTENT_TYPE_KEY.getHeader(), TripleHeaderEnum.CONTENT_PROTO.getHeader());
        transportListener.onHeader(headers, false);
        Assertions.assertTrue(listener.started);
        stream.requestN(2);
        byte[] data = new byte[]{0, 0, 0, 0, 1, 1};
        final ByteBuf buf = Unpooled.wrappedBuffer(data);
        transportListener.onData(buf, false);
        buf.release();
        Assertions.assertEquals(1, listener.message.length);
    }
}