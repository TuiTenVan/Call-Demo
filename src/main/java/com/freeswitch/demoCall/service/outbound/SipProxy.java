package com.freeswitch.demoCall.service.outbound;


import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.service.processor.CallInMgr;
import com.freeswitch.demoCall.utils.SipUtil;
import io.pkts.buffer.Buffers;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipRequest;
import io.pkts.packet.sip.SipResponse;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jxmpp.stringprep.XmppStringprepException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SipProxy {

    private static final Logger logger = LogManager.getLogger(SipProxy.class);

    private final AtomicLong INCR = new AtomicLong();

    public static final String CALL_PSTN_JID_CALLIN = "callin@call.";

    @Value("${ringme.domain.xmpp}")
    private String domainXmpp;



    public static void send(SipMessageEvent sipEvent, SipMessage rq) {
        if (sipEvent != null && rq != null) {
            sipEvent.getConnection().send(rq);
            if (!rq.isOptions()) {
                String caller = SipUtil.getFromUser(rq);
                String callee = SipUtil.getToUser(rq);
                String callID = SipUtil.getCallID(rq);
                String method = rq.getMethod().toString();
                String initialLine = rq.getInitialLine().toString();
                logger.info("{},{},{},{},{},{}", true, method, caller, callee, callID, initialLine);
            }

        }
    }

    public void processEvents(final SipMessageEvent event) {
        try {
            final SipMessage msg = event.getMessage();
            logRecvSipEvents(msg);
            if (msg.isAck()) {
                return;
            }
            if (msg.isInvite()) {
                onReceiveInvite(event);
            } else if (msg.isBye() && msg.isRequest()) {
                onReceiveBye(event);
            } else if (msg.isCancel()) {
                onReceiveCancel(event);
            } else if (msg.isRequest()) {
                final SipResponse response = msg.createResponse(200);
                SipProxy.send(event, response);
            }
        } catch (Exception e) {
            logger.error("|SipProxyProcessor|Exception: " + e.getMessage(), e);
        }
    }

    private void onReceiveInvite(SipMessageEvent event) {
        /* STEP 1: RESPONSE 100 TRYING */
        final SipResponse trying100 = event.getMessage().createResponse(100);
        SipProxy.send(event, trying100);

        /* STEP 2: Process */
        event.getMessage().getToHeader().setParameter(Buffers.wrap("tag"), Buffers.wrap(SipUtil.randomTag()));
        SipRequest req = (SipRequest) event.getMessage();
        String caller = SipUtil.getFromUser(req);
        String callee = SipUtil.getToUser(req);
        String callID = SipUtil.getCallID(req);
//        handleBridge(event, caller, callee, callID);
    }

    private void handleBridge(SipMessageEvent event, String caller, String callee, String callID) {
        final String PREFIX_LOG_CALLIN = "BRIDGE_CALL|" + caller + "|" + callee + "|" + callID + "|";
        logger.info("{}START", PREFIX_LOG_CALLIN);

//        UserInfo userInfo = userInfoDao.getUserInfoByUsername(callee);
//        logger.info("{}{}: {}", PREFIX_LOG_CALLIN, callee, userInfo);

        UA ua = new UA(event);
//        ua.setIvr(true);
        ua.setCaller(caller);
        ua.setCallee("+84911111121");
        ua.setJid(callee + "@192.168.1.78");
        CallInMgr.instance().put(ua);


        Message message = makeXmppMessageCallinInvitte(caller,
                ua.getCallerName(),
                ua.getCallee(), ua.getJid(), getSdp(event),
                callID, null, false);

        sendMessage(message);
        logger.info("{}SEND INVITE MESSAGE TO XMPP", PREFIX_LOG_CALLIN);
    }

    private void onReceiveCancel(SipMessageEvent event) {
        final SipResponse response = event.getMessage().createResponse(200);
        SipProxy.send(event, response);

        SipRequest req = (SipRequest) event.getMessage();
        String caller = SipUtil.getFromUser(req);
        String callee = SipUtil.getToUser(req);
        String callID = SipUtil.getCallID(req);
//        String reason = SipUtil.getReason(req);


    }

    private void onReceiveBye(SipMessageEvent event) {
        final SipResponse response = event.getMessage().createResponse(200);
        SipProxy.send(event, response);

        SipRequest req = (SipRequest) event.getMessage();
        String caller = SipUtil.getFromUser(req);
        String callee = SipUtil.getToUser(req);
        String callID = SipUtil.getCallID(req);
        String reason = SipUtil.getReason(req);

        UA ua = CallInMgr.instance().remove(callID);
        if (ua != null) {
            final String PREFIX_LOG_CALLIN = "CALLIN|" + caller + "|" + ua.getCallee() + "|" + callID + "|";
            Message message = makeXmppMessageCallin(caller, ua.getCallerName(), ua.getCallee(), ua.getJid(), "203",
                    SipUtil.getCallID(event.getMessage()), null);

            sendMessage(message);
            logger.info(PREFIX_LOG_CALLIN + "SEND BYE 203 CALLIN MESSAGE TO XMPP");

            if (ua.isIvr()) {
//                redisQueueService.cacheAgentAfterHangup(ua.getJid(), 60, ua.getCaller());
            }
        }
    }

    public void sendMessage(Message message) {
        try {
            XmlStringBuilder stringBuilder = (XmlStringBuilder) message.toXML();
//            kafkaTemplate.send(kafka_topic, stringBuilder.toString());
            logger.info("push2Queue|Message|" + stringBuilder);
        } catch (Exception e) {
            logger.error("push2Queue|Exception|" + e.getMessage(), e);
        }
    }

    private String getSdp(SipMessageEvent event) {
        return event.getMessage().getRawContent().toString();
    }

    public Message makeXmppMessageCallinInvitte(String caller,
                                                String callerName,
                                                String callee,
                                                String to,
                                                String sdp,
                                                String callID,
                                                String additionaldata,
                                                boolean isAppToApp) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(SipProxy.CALL_PSTN_JID_CALLIN + domainXmpp)
                    .to(to)
                    .ofType(Message.Type.chat)
                    .setBody("callin")
                    .build();
            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callin")
                    .build();

            StandardExtensionElement hint = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();

            StandardExtensionElement extData = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:data")
                    .addAttribute("code", "100")
                    .setText(sdp)
                    .build();

            StandardExtensionElement extCaller = StandardExtensionElement.builder(
                            "caller", "urn:xmpp:ringme:caller")
                    .addAttribute("name", callerName != null ? callerName : caller)
                    .setText(caller)
                    .build();
            StandardExtensionElement.Builder extCalldataBuilder = StandardExtensionElement.builder(
                            "callin", "urn:xmpp:ringme:callin")
                    .addElement(extData)
                    .addElement(extCaller)
                    .addElement("callee", callee)
                    .addElement("session", callID);

            if (additionaldata != null) {
                extCalldataBuilder.addElement("additionaldata", additionaldata);
            }

            if (isAppToApp) {
                extCalldataBuilder.addElement("apptoapp", "1");
            }
            StandardExtensionElement extCalldata = extCalldataBuilder.build();
            message.addExtension(extContentType);
            message.addExtension(hint);
            message.addExtension(extCalldata);

            StandardExtensionElement shouldNotify = StandardExtensionElement.builder(
                            "should-notify", "urn:xmpp:hints")
                    .build();
            message.addExtension(shouldNotify);
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    public Message makeXmppMessageCallin(String caller,
                                         String callerName,
                                         String callee,
                                         String to, String status, String callID, String additionaldata) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(SipProxy.CALL_PSTN_JID_CALLIN + domainXmpp)
                    .to(to)
                    .ofType(Message.Type.chat)
                    .setBody("callin")
                    .build();
            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callin")
                    .build();

            StandardExtensionElement extData = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:data")
                    .addAttribute("code", status)
                    .build();

            StandardExtensionElement.Builder extCalldataBuilder = StandardExtensionElement.builder(
                            "callin", "urn:xmpp:ringme:callin")
                    .addElement(extData)
                    .addElement("session", callID);

            if (caller != null) {
                StandardExtensionElement extCaller = StandardExtensionElement.builder(
                                "caller", "urn:xmpp:ringme:caller")
                        .addAttribute("name", callerName != null ? callerName : caller)
                        .setText(caller)
                        .build();
                extCalldataBuilder.addElement(extCaller);
            }
            if (callee != null) {
                extCalldataBuilder.addElement("callee", callee);
            }
            if (additionaldata != null) {
                extCalldataBuilder.addElement("additionaldata", additionaldata);
            }
            message.addExtension(extContentType);
            message.addExtension(extCalldataBuilder.build());


            StandardExtensionElement extNoStore = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();
            message.addExtension(extNoStore);

            if (status.equals("486")) {
                StandardExtensionElement shouldNotify = StandardExtensionElement.builder(
                                "should-notify", "urn:xmpp:hints")
                        .build();
                message.addExtension(shouldNotify);
            }
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    private void pushRedisNotify(String calleeUsername,
                                 String session,
                                 String status,
                                 Message message) {

//        callService.pushRedisNotify(calleeUsername, session, status, message);
    }

    private void logRecvSipEvents(final SipMessage msg) {
        if (msg.isOptions()) {
            return;
        }
        logger.info("|SIP_PROXY_RECEIVE|" + msg);
//        if (msg.isInvite() || msg.isBye() || msg.isCancel()) {
//            String caller = SipUtil.getFromUser(msg);
//            String callee = SipUtil.getToUser(msg);
//            String callID = SipUtil.getCallID(msg);
//            logCDR(false, msg.getMethod().toString(), caller, callee, callID, "");
//            logger.info("|SEND_SIP|" + msg);
//        }

    }
}

