/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.turn.ttorrent.client.io;

import com.turn.ttorrent.client.peer.PeerConnectionListener;
import com.turn.ttorrent.client.peer.PeerHandler;
import com.turn.ttorrent.client.peer.PeerMessageListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.logging.LoggingHandler;
import javax.annotation.Nonnull;

/**
 *
 * @author shevek
 */
public abstract class PeerHandshakeHandler extends ChannelInboundHandlerAdapter {

    protected static final PeerFrameEncoder frameEncoder = new PeerFrameEncoder();

    @Nonnull
    protected abstract LoggingHandler getWireLogger();

    @Nonnull
    protected abstract LoggingHandler getMessageLogger();

    @Nonnull
    protected ByteBuf toByteBuf(@Nonnull HandshakeMessage message) {
        ByteBuf buf = Unpooled.buffer(HandshakeMessage.BASE_HANDSHAKE_LENGTH + 64);
        message.toWire(buf);
        return buf;
    }

    protected void addMessageHandlers(@Nonnull ChannelPipeline pipeline, @Nonnull PeerMessageListener listener) {
        pipeline.addLast(new CombinedChannelDuplexHandler(new PeerFrameDecoder(), frameEncoder));
        pipeline.addLast(new PeerMessageCodec());
        pipeline.addLast(getMessageLogger());
        pipeline.addLast(new PeerMessageHandler(listener));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addFirst(getWireLogger());
        super.channelRegistered(ctx);
    }

    protected abstract void process(@Nonnull ChannelHandlerContext ctx, @Nonnull HandshakeMessage message);

    protected void addPeer(@Nonnull ChannelHandlerContext ctx, @Nonnull HandshakeMessage message,
            @Nonnull PeerConnectionListener listener) {
        Channel channel = ctx.channel();
        PeerHandler peer = listener.handlePeerConnectionCreated(channel, message.getPeerId());
        if (peer == null) {
            ctx.close();
            return;
        }

        addMessageHandlers(ctx.pipeline(), peer);
        ctx.pipeline().remove(this);

        listener.handlePeerConnectionReady(peer);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        if (in.readableBytes() < HandshakeMessage.BASE_HANDSHAKE_LENGTH)
            return;

        int length = in.getUnsignedByte(0);
        if (in.readableBytes() < HandshakeMessage.BASE_HANDSHAKE_LENGTH + length)
            return;

        HandshakeMessage request = new HandshakeMessage();
        request.fromWire(in);

        process(ctx, request);
    }
}