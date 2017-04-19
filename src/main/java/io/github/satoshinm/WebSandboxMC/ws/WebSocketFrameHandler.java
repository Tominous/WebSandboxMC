/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.github.satoshinm.WebSandboxMC.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.logging.Level;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final WebSocketServerThread webSocketServerThread;

    public WebSocketFrameHandler(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
    }

    @Override
    @SuppressWarnings("deprecation") // TODO: why is HANDSHAKE_COMPLETE deprecated and what is the replacement?
    public void userEventTriggered(final ChannelHandlerContext ctx, Object evt) throws Exception {
        webSocketServerThread.log(Level.FINEST, "userEventTriggered: "+evt);
        if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            // "The Handshake was complete successful and so the channel was upgraded to websockets"

            // Since we're in a callback we cannot call any Bukkit API safely here, see:
            // http://bukkit.gamepedia.com/Scheduler_Programming#Tips_for_thread_safety
            // " Warning:	Asynchronous tasks should never access any API in Bukkit"
            webSocketServerThread.scheduleSyncTask(new Runnable() {
                @Override
                public void run() {
                    webSocketServerThread.handleNewClient(ctx);
                }
            });
        }
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        webSocketServerThread.log(Level.FINEST, "channel read, frame="+frame);

        if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = frame.content();

            byte[] bytes = new byte[content.capacity()];
            content.getBytes(0, bytes);

            final String string = new String(bytes);
            webSocketServerThread.log(Level.FINEST, "received "+content.capacity()+" bytes: "+string);

            this.webSocketServerThread.scheduleSyncTask(new Runnable() {
                @Override
                public void run() {
                    webSocketServerThread.handle(string, ctx);
                }
            });
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        webSocketServerThread.scheduleSyncTask(new Runnable() {
            @Override
            public void run() {
                webSocketServerThread.webPlayerBridge.clientDisconnected(ctx.channel());
            }
        });
    }
}
