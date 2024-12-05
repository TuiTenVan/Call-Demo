package com.freeswitch.demoCall.service.callin;

import com.freeswitch.demoCall.utils.Utils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.internal.debug.ExecutionHandler;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.outbound.AbstractOutboundPipelineFactory;
import org.freeswitch.esl.client.outbound.SocketClient;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslFrameDecoder;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

public class TestOutbound {

    public static void main(String[] args) {
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
                return new ESLOutboundHandler2();
            }
        });
        outboundServer.start();
    }

    @Data
    @NoArgsConstructor
    public static class ESLOutboundHandler2 extends AbstractOutboundClientHandler {

        private final Logger logger = LogManager.getLogger(ESLOutboundHandler2.class);

        @Override
        protected void handleConnectResponse(ChannelHandlerContext ctx, EslEvent event) {
            logger.info(event.getEventHeaders().get("Answer-State"));
            if (event.getEventName().equalsIgnoreCase("CHANNEL_DATA")) {
                if ("ringing".equalsIgnoreCase(event.getEventHeaders().get("Answer-State")) ||
                        event.getEventHeaders().get("variable_conference_uuid") != null) {
                    String PREFIX_LOG_CALLIN = "TEST|";
                    if (EventSocketAPI.runCommand(ctx, "set", "call_timeout=10", true)) {
                        logger.info(PREFIX_LOG_CALLIN + "call_timeout=10");
                    }
//                    String linkAudio = Utils.getLink("/version-huyen/cms-sdk-upload/ivrFile/2024/10/18/1jfxnkuwjwidduehl4p186ont79dirfq.wav", true);
                    if (EventSocketAPI.runCommand(ctx, "export", "ringback=/etc/freeswitch/call-record/sounds/ringtone_8000_mono.wav", true)) {
                        logger.info(PREFIX_LOG_CALLIN + "ringback");
                    }
//
//                    if (EventSocketAPI.runCommand(ctx, "set", "instant_ringback=true", true)) {
//                        logger.info(PREFIX_LOG_CALLIN + "instant_ringback=true");
//                    }
//
//                    if (EventSocketAPI.runCommand(ctx, "ring_ready", null, true)) {
//                        logger.info(PREFIX_LOG_CALLIN + "ring_ready");
//                    }

                    if (EventSocketAPI.runCommand(ctx, "sleep", "5000", true)) {
                        logger.info(PREFIX_LOG_CALLIN + "sleep 10000");
                    }
//                    String uuidDisplace = "uuid_displace ${uuid} start " + linkAudio + " 0 loop";
//                    if (EventSocketAPI.runCommand(ctx, "export", "api_result=${" + uuidDisplace + "}", false)) {
//                        logger.info(PREFIX_LOG_CALLIN + "uuid_displace");
//                    }


//                    String uuidDisplace = "uuid_displace(${uuid} start /etc/freeswitch/sounds/ringme-info-ivr.wav 0 loop)";
//                    String uuidDisplaceStop = "uuid_displace ${uuid} stop /etc/freeswitch/sounds/ringme-info-ivr.wav 0 loop";

//                    if (EventSocketAPI.runCommand(ctx, "export",
//                            "api_result=${" + uuidDisplace + "}", false)) {
//                        logger.info(PREFIX_LOG_CALLIN + "export");
//                    }

//                    String bridgeTo = "{api_on_answer='" + uuidDisplaceStop + "'}user/1001";

                    String bridgeTo = "user/1001";
                    for (int i = 0; i < 3; i++) {

                        if (EventSocketAPI.runCommand(ctx, "bridge", bridgeTo, true)) {
                            logger.info(PREFIX_LOG_CALLIN + "bridge user/1001");
                        }
                    }

//                    String appArg = EventSocketAPI.makePlayAndGetDigitCommand(1,
//                            9, 1, 3000, "#",
//                            "/etc/freeswitch/sounds/ringme-info-ivr.wav",
//                            null, "ringmeditgit", "\\d+", 2000);
//
//                    if (EventSocketAPI.runCommand(ctx, "play_and_get_digits", appArg, false)) {
//                        logger.info(PREFIX_LOG_CALLIN + "play_and_get_digits|{}", appArg);
//                    }

                    //
                }
            } else {
                logger.warn("Unexpected event after connect: [" + event.getEventName() + ']');
            }
//        printLog(event);
        }

        @Override
        protected void handleEslEvent(ChannelHandlerContext ctx, EslEvent eslEvent) {
            logger.info("handleEslEvent" + "|" + ctx.getName() + "|" + ctx.getChannel() + "|" + eslEvent.toString());
//        printLog(eslEvent);
        }


        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
            logger.info("messageReceived: {}", e.getMessage());
        }

        @Override
        protected void handleEslMessage(ChannelHandlerContext ctx, EslMessage message) {
            super.handleEslMessage(ctx, message);
//        printLog(message);
        }
    }
}