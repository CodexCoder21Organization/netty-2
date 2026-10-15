/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioShutdownReentryTest {
    @Test
    public void closeListenersRegisteringMoreChannelsCannotLeaveAcceptedChannelsOpen() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        List<SocketChannel> sockets = new ArrayList<>();
        List<Channel> channels = new ArrayList<>();
        List<ChannelFuture> registrations = new ArrayList<>();
        try {
            EventLoop loop = group.next();
            for (int i = 0; i < 8; i++) {
                SocketChannel socket = SocketChannel.open();
                sockets.add(socket);
                channels.add(new NioSocketChannel(socket));
            }
            for (int i = 0; i < channels.size() - 1; i++) {
                final Channel next = channels.get(i + 1);
                channels.get(i).closeFuture().addListener(future -> registrations.add(loop.register(next)));
            }
            loop.submit(() -> {
                registrations.add(loop.register(channels.get(0)));
                group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }).get(5, TimeUnit.SECONDS);
            assertTrue(group.terminationFuture().await(10, TimeUnit.SECONDS));
            assertTrue(registrations.get(0).isSuccess());
            for (ChannelFuture registration : registrations) {
                assertTrue(registration.isDone());
                Channel channel = registration.channel();
                assertFalse(channel.isOpen(), "A channel accepted during shutdown must close before termination");
            }
            for (ChannelFuture registration : registrations) {
                Channel channel = registration.channel();
                assertTrue(channel.closeFuture().isDone());
                assertFalse(channel.isRegistered());
            }
        } finally {
            for (SocketChannel socket : sockets) {
                socket.close();
            }
            assertTrue(group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void unregisterCallbacksRegisteringMoreChannelsCannotLeaveRegistrationsBehind() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        List<SocketChannel> sockets = new ArrayList<>();
        List<Channel> channels = new ArrayList<>();
        List<ChannelFuture> registrations = new ArrayList<>();
        try {
            EventLoop loop = group.next();
            for (int i = 0; i < 8; i++) {
                SocketChannel socket = SocketChannel.open();
                sockets.add(socket);
                channels.add(new NioSocketChannel(socket));
            }
            for (int i = 0; i < channels.size() - 1; i++) {
                final Channel next = channels.get(i + 1);
                channels.get(i).pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelUnregistered(ChannelHandlerContext ctx) {
                        registrations.add(loop.register(next));
                        ctx.fireChannelUnregistered();
                    }
                });
            }
            loop.submit(() -> {
                registrations.add(loop.register(channels.get(0)));
                group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }).get(5, TimeUnit.SECONDS);
            assertTrue(group.terminationFuture().await(10, TimeUnit.SECONDS));
            assertTrue(registrations.get(0).isSuccess());
            for (ChannelFuture registration : registrations) {
                assertTrue(registration.isDone());
                Channel channel = registration.channel();
                assertFalse(channel.isRegistered(), "Termination must drain callback-created channel deregistration");
                assertFalse(channel.isOpen());
                assertTrue(channel.closeFuture().isDone());
            }
        } finally {
            for (SocketChannel socket : sockets) {
                socket.close();
            }
            assertTrue(group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closeListenerCanStillRegisterAChannelOnALiveLoop() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try (SocketChannel firstSocket = SocketChannel.open(); SocketChannel secondSocket = SocketChannel.open()) {
            Channel first = new NioSocketChannel(firstSocket);
            Channel second = new NioSocketChannel(secondSocket);
            AtomicReference<ChannelFuture> registration = new AtomicReference<>();
            EventLoop loop = group.next();
            loop.register(first).sync();
            first.closeFuture().addListener(future -> registration.set(loop.register(second)));
            first.close().sync();
            loop.submit(() -> { }).sync();
            assertTrue(registration.get().isSuccess());
            assertTrue(second.isOpen());
            assertTrue(second.isRegistered());
            second.close().sync();
        } finally {
            assertTrue(group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS));
        }
    }
}
