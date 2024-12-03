package com.freeswitch.demoCall.service.callin;

import org.freeswitch.esl.client.internal.debug.ExecutionHandler;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.outbound.AbstractOutboundPipelineFactory;
import org.freeswitch.esl.client.outbound.SocketClient;
import org.freeswitch.esl.client.transport.message.EslFrameDecoder;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class ESLOutboundListenerService {
    @Autowired
    private ApplicationContext context;

    public void run() {
        final SocketClient outboundServer = new SocketClient(8091, new AbstractOutboundPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("encoder", new StringEncoder());
                pipeline.addLast("decoder", new EslFrameDecoder(16384, true));
                pipeline.addLast("executor", new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576L, 1048576L)));
                pipeline.addLast("clientHandler", this.makeHandler());
                return pipeline;
            }

            @Override
            protected AbstractOutboundClientHandler makeHandler() {
                return new ESLOutboundHandler(context);
            }
        });
        outboundServer.start();
    }
}
