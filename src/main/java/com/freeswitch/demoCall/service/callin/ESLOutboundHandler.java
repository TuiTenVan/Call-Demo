package com.freeswitch.demoCall.service.callin;

import com.freeswitch.demoCall.service.callin.queue.CallToQueueServiceV2;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.springframework.context.ApplicationContext;

@Getter
@Setter
@NoArgsConstructor
@Slf4j

public class ESLOutboundHandler extends AbstractOutboundClientHandler {

    private final Logger logger = LogManager.getLogger(ESLOutboundHandler.class);
    private ApplicationContext context;
    private CallToQueueServiceV2 callToQueue;
    private RedisQueueService redisQueueService;
    private RedisQueueSessionService redisQueueSessionService;

    public ESLOutboundHandler(ApplicationContext context) {
        this.redisQueueService = context.getBean(RedisQueueService.class);
        this.callToQueue = context.getBean(CallToQueueServiceV2.class);
        this.redisQueueSessionService = context.getBean(RedisQueueSessionService.class);
    }

    @Override
    protected void handleConnectResponse(ChannelHandlerContext ctx, EslEvent event) {
        logger.info(event.getEventHeaders().get("Answer-State"));
        if (event.getEventName().equalsIgnoreCase("CHANNEL_DATA")) {
            if ("ringing".equalsIgnoreCase(event.getEventHeaders().get("Answer-State")) ||
                    event.getEventHeaders().get("variable_conference_uuid") != null) {
                doBridgeCall(ctx, event);
            }
        } else {
            logger.warn("Unexpected event after connect: [{}]", event.getEventName());
        }
    }

    @Override
    protected void handleEslEvent(ChannelHandlerContext ctx, EslEvent eslEvent) {
//        logger.info("handleEslEvent|{}|{}|{}", ctx.getName(), ctx.getChannel(), eslEvent.toString());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.messageReceived(ctx, e);
//        logger.info("messageReceived: {}", e.getMessage());
    }

    @Override
    protected void handleEslMessage(ChannelHandlerContext ctx, EslMessage message) {
        super.handleEslMessage(ctx, message);
    }
    @Override
    protected void handleDisconnectionNotice() {
        logger.warn("Disconnected from FreeSWITCH.");
    }

    private void doBridgeCall(ChannelHandlerContext ctx, EslEvent event) {
//        logger.info(event.getEventHeaders());
        String caller = event.getEventHeaders().get("Channel-ANI");
        String callee = event.getEventHeaders().get("Channel-Destination-Number");
        String uniqueID = event.getEventHeaders().get("Unique-ID");
        String sipCallId = event.getEventHeaders().get("variable_sip_call_id");
        logger.info("caller={}|callee={}|callId={}", caller, callee, uniqueID);
        final String PREFIX_LOG_CALLIN = "CALLIN|" +
                caller + "|" +
                callee + "|";
        EventSocketAPI.runCommand(ctx, "answer", null, false);
        redisQueueSessionService.setCallIdIvrByCaller(caller, sipCallId);
    }
}
