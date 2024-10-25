package com.freeswitch.demoCall.Service.Outbound;

import com.freeswitch.demoCall.Service.Outbound.queue.CallToQueue;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@Slf4j

public class ESLOutboundHandler extends AbstractOutboundClientHandler {

    private final Logger logger = LogManager.getLogger(ESLOutboundHandler.class);
    private ApplicationContext context;
    private CallToQueue callToQueue;

    public ESLOutboundHandler(ApplicationContext context) {
        this.callToQueue = context.getBean(CallToQueue.class);
    }

    @Override
    protected void handleConnectResponse(ChannelHandlerContext ctx, EslEvent event) {
        logger.info(event.getEventHeaders());
    }

    @Override
    protected void handleEslEvent(ChannelHandlerContext ctx, EslEvent eslEvent) {
        logger.info("handleEslEvent|{}|{}|{}", ctx.getName(), ctx.getChannel(), eslEvent.toString());
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
    @Override
    protected void handleDisconnectionNotice() {
        logger.warn("Disconnected from FreeSWITCH.");
    }

    private void doBridgeCall(ChannelHandlerContext ctx, EslEvent event) {

//        Configuration cfg = AppContext.getInstance().getContext().getBean(Configuration.class);
        logger.info(event.getEventHeaders());
        String caller = event.getEventHeaders().get("Channel-ANI");
        String callee = event.getEventHeaders().get("Channel-Destination-Number");
        String uniqueID = event.getEventHeaders().get("Unique-ID");
        logger.info("caller={}|callee={}|callId={}", caller, callee, uniqueID);

//        if (processTransferCall(ctx, event, caller, callee, uniqueID)) {
//            return;
//        }
//        IVRHotline ivrHotline = redisQueueService.getCacheIvrHotLine(event.getEventHeaders().get("Channel-Destination-Number"));
//        if (ivrHotline == null) {
//            ivrHotline = ivrDao.getIvrHotlineByHotline(event.getEventHeaders().get("Channel-Destination-Number"));
//        }
//        if (ivrHotline != null) {
//
//            logger.info("caller={}|callee={}|callId={}", caller, callee, uniqueID);
//            processIVRCall(caller, callee, uniqueID, event, ctx, ivrHotline);
//            return;
//        }
//
//        Map<String, String> map = redisService.getRecentCalleeForCallin(caller, false);
//
//        if (map == null) {
//            // response cancel
//            final String PREFIX_LOG_CALLIN = "CALLIN|" +
//                    caller + "|" +
//                    callee + "|";
//            logger.info(PREFIX_LOG_CALLIN + "Not have recent call for caller");
//            boolean isPlayToneStreamSuccess = EventSocketAPI.playToneStream(ctx, "not_found_postman.wav", 3);
//            if (isPlayToneStreamSuccess) {
//
//                logger.info(PREFIX_LOG_CALLIN + "RESPONSE_CANCEL|PLAY_TONE_STREAM|" + caller + "|" + callee + "|" + uniqueID + "|" + "vm-cancel_voice2.wav");
//            }
//        } else {
//            final String PREFIX_LOG_CALLIN = "CALLIN|" +
//                    caller + "|" +
//                    map.get("callee");
//
//
//            String variableCallee = "ringme_dest_callee=" + map.get("callee");
//            boolean isSetCallee = EventSocketAPI.setVariables(ctx, variableCallee);
//            if (isSetCallee) {
//                logger.info(PREFIX_LOG_CALLIN + "|SET_CALLEE|" + caller + "|" + variableCallee + "|" + uniqueID);
//            }
//
//
//            Map<String, String> args = new HashMap<>(1);
//            args.put("profile", "external");
//            args.put("freecall", "1");
//
//            String fsGw = "{media_webrtc=true}sofia/gateway/ringme_callin/";
//
//            boolean isBridgeSuccess = EventSocketAPI.bridgeCall(ctx, fsGw + callee, args);
//            if (isBridgeSuccess) {
//                logger.info(PREFIX_LOG_CALLIN + "|BRIDGE_CALL|" + caller + "|" + callee + "|" + uniqueID + "|" + fsGw + callee);
//            }
//        }
//    }
    }
}
