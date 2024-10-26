package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.service.outbound.ESLOutboundListenerService;
import com.freeswitch.demoCall.service.outbound.SipProxy;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.sipstack.netty.codec.sip.SipMessageDatagramDecoder;
import io.sipstack.netty.codec.sip.SipMessageEncoder;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;


@Component
public class AppStartupRunner implements ApplicationRunner {
    private final Logger logger = LogManager.getLogger(AppStartupRunner.class);
    @Autowired
    private ESLOutboundListenerService eslOutboundListenerService;

    @Autowired
    private LeaderElectionStartupRunner leaderElectionStartupRunner;

    @Autowired
    private SipProxy sipProxy;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("======================>" + "AppStartupRunner");
        eslOutboundListenerService.run();
        leaderElectionStartupRunner.startLeaderElectionWatcher();
        startSipUASListener();
    }
    private void startSipUASListener() {
        logger.info("startSipUASListener|init");
        final SimpleChannelInboundHandler<SipMessageEvent> uas = new SimpleChannelInboundHandler<SipMessageEvent>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, // (3)
                                        final SipMessageEvent event) throws Exception {
                try {
                    sipProxy.processEvents(event);
                } catch (Exception e) {
                    logger.error("|SimpleChannelInboundHandler|Exception|" + e.getMessage(), e);
                }

            }
        }; // (1)
        final EventLoopGroup udpGroup = new NioEventLoopGroup(); // (2)

        final Bootstrap b = new Bootstrap(); // (3)
        b.group(udpGroup).channel(NioDatagramChannel.class).handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(final DatagramChannel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new SipMessageDatagramDecoder()); // (4)
                pipeline.addLast("encoder", new SipMessageEncoder()); // (5)
                pipeline.addLast("handler", uas); // (6)
            }
        });

        try {
            final InetSocketAddress socketAddress = new InetSocketAddress(8092); // (7)
            final ChannelFuture f = b.bind(socketAddress).sync(); // (8)
            f.channel().closeFuture().await();
            logger.info("startSipUASListener|success");
        } catch (Exception e) {
            logger.error("startSipUASListener: {}", e.getMessage(), e);
        }
    }

}
