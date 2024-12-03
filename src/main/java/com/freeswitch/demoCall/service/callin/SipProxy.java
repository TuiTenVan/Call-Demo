package com.freeswitch.demoCall.service.callin;

import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.mysql.dao.CallCDRDao;
import com.freeswitch.demoCall.mysql.dao.UserInfoDao;
import com.freeswitch.demoCall.mysql.entity.CallCDR;
import com.freeswitch.demoCall.mysql.entity.UserInfo;
import com.freeswitch.demoCall.service.CallService;
import com.freeswitch.demoCall.service.RedisService;
import com.freeswitch.demoCall.service.callin.queue.CallQueueNotifyService;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.service.processor.CallInMgr;
import com.freeswitch.demoCall.utils.SipUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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

    @Value("${ringme.kafka.topic.pstn2webrtc}")
    private String kafka_topic;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private UserInfoDao userInfoDao;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    @Autowired
    private CallService callService;

    @Autowired
    private CallQueueNotifyService callQueueNotifyService;

    @Autowired
    private Gson gson;

    public static void send(SipMessageEvent sipEvent, SipMessage rq) {
        if (sipEvent != null && rq != null) {
            sipEvent.getConnection().send(rq);
            if (!rq.isOptions()) {
                String caller = SipUtil.getFromUser(rq);
                String callee = SipUtil.getToUser(rq);
                String callID = SipUtil.getCallID(rq);
                String method = rq.getMethod().toString();
                String initialLine = rq.getInitialLine().toString();
                logger.info("send SipMessage to FS: {},{},{},{},{},{}", true, method, caller, callee, callID, initialLine);
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

        if (!CallInMgr.instance().containsKey(callID)) {

            if (callee.startsWith("queue_")) {

                handleCallIvr(event, caller, callee, callID);
                return;
            }
            Map<String, String> map = redisService.getRecentCalleeForCallin(caller, true);

            if (map == null) {
                // response cancel
                final SipMessage msg = event.getMessage();
                final SipResponse response = msg.createResponse(486);
                SipProxy.send(event, response);
                logger.info("CALLIN|BRIDGE CALL FAIL|RECENT CALL NOT EXIST");
            } else {

                final String PREFIX_LOG_CALLIN = "CALLIN|" + caller + "|" + map.get("callee") + "|" + callID + "|";

                logger.info(PREFIX_LOG_CALLIN + "HOTLINE=" + callee + "|jid=" + map.get("jid"));
                UA ua = new UA(event);
                ua.setCallee(map.get("callee"));
                ua.setJid(map.get("jid"));
                ua.setCallerName(map.get("calleeName"));

                String additionaldata = map.get("additionaldata") != null ? map.get("additionaldata") : null;
                if (additionaldata != null) {
                    ua.setAdditionaldata(additionaldata);
                }
                // cancel callin if client offline not working true callid
                redisService.putRecentCallinCallId(caller, callID);

//                redisService.putCDRInviteCallinToRedis(map.get("callee"), callID,  map.get("jid"), event);
//                ua.setNumOfOwner(numOfOwner);
                CallInMgr.instance().put(ua);

                logger.info(PREFIX_LOG_CALLIN + "PUT UA");

                UserInfo userInfo = userInfoDao.getUserInfoByUsername(ua.getJid().split("@")[0]);
                logger.info(PREFIX_LOG_CALLIN + "USER_INFO|{}", userInfo);
                CallCDR callCDR = CallCDR.builder()
                        .sessionId(callID)
                        .calleeUsername(ua.getJid().split("@")[0])
                        .callerPhoneNumber(caller)
                        .calleePhoneNumber(ua.getCallee())
                        .calleeIdDepartment(userInfo.getIdDepartment())
                        .calleeIdProvince(userInfo.getIdProvince())
                        .calleeAppId(userInfo.getAppId())
                        .calleeType(userInfo.getType())
                        .calleePosition(userInfo.getPosition())
                        .callType("callin")
                        .hotline(callee)
                        .callStatus("invite")
                        .createdAt(new Date())
                        .ownerId(userInfo.getOwnerId())
                        .build();


                callCDR.setNetworkType(callCDR.getMnpFrom().equals(callCDR.getMnpTo()) ? "on-net" : "off-net");

                if (map.get("order") != null) {
                    callCDR.setOrderId(map.get("order"));
                }
                if (additionaldata != null) {
                    callCDR.setAdditionalData(additionaldata);
                }

                Message message = makeXmppMessageCallinInvite(caller,
                        ua.getCallerName(), null,
                        ua.getCallee(), ua.getJid(), getSdp(event, null),
                        callID, additionaldata, false, false);
                pushRedisNotify(ua.getJid(), callID, "100", message);
                sendMessage(message);
                logger.info(PREFIX_LOG_CALLIN + "SEND INVITE CALLIN MESSAGE TO XMPP");
            }
        }
    }

    private void handleCallIvr(SipMessageEvent event, String caller, String callee, String callID) {
        final String PREFIX_LOG_CALLIN = "CALL_IVR|" + caller + "|" + callee + "|" + callID + "|";
        logger.info(PREFIX_LOG_CALLIN + "START");

        if (callee.equals("queue_ringing")) {
            SipResponse response = event.getMessage().createResponse(180);
            SipProxy.send(event, response);
            return;
        }
        String usernameCallee = callee.replace("queue_", "").replace("_rob", "");
        UserInfo userInfo = userInfoDao.getUserInfoByUsername(usernameCallee);
        logger.info(PREFIX_LOG_CALLIN + "{}: {}", usernameCallee, userInfo);


        String originCallId = redisQueueSessionService.getCallIdIvrByCaller(caller);

        UA ua = new UA(event);
        ua.setIvr(!callee.contains("_rob"));
        ua.setCaller(caller);
        ua.setCallee(userInfo.getPhoneNumber());
        ua.setJid(userInfo.getUsername() + "@" + userInfo.getDomain());
        CallInMgr.instance().put(ua);

        callService.cacheAgentInfoForCallId(originCallId, userInfo);
        redisQueueService.cacheAgentCalling(ua.getCallee());

        boolean isAppToApp = event.getMessage().getHeader("P-Early-Media") == null;

        String additionaldata = redisQueueSessionService.getCallOrderIvrByCaller(caller);

        Message message = makeXmppMessageCallinInvite(caller,
                ua.getCallerName(), originCallId,
                ua.getCallee(), ua.getJid(), getSdp(event, originCallId),
                callID, additionaldata, isAppToApp, callee.contains("_rob"));

        sendMessage(message);
        logger.info(PREFIX_LOG_CALLIN + "SEND INVITE MESSAGE TO XMPP");
    }

    private void onReceiveCancel(SipMessageEvent event) {
        final SipResponse response = event.getMessage().createResponse(200);
        SipProxy.send(event, response);

        SipRequest req = (SipRequest) event.getMessage();
        String caller = SipUtil.getFromUser(req);
        String callee = SipUtil.getToUser(req);
        String callID = SipUtil.getCallID(req);
//        String reason = SipUtil.getReason(req);

        UA ua = CallInMgr.instance().remove(callID);
        if (ua != null) {
            final String PREFIX_LOG_CALLIN = "CALLIN|" + caller + "|" + ua.getCallee() + "|" + callID + "|";

            Message message = makeXmppMessageCallin(caller, ua.getCallerName(), ua.getCallee(), ua.getJid(), "486",
                    SipUtil.getCallID(event.getMessage()), ua.getAdditionaldata());

            pushRedisNotify(ua.getJid(), callID, "486", message);
            sendMessage(message);
            logger.info(PREFIX_LOG_CALLIN + "SEND CANCEL 486 CALLIN MESSAGE TO XMPP");

            redisQueueService.removeCacheAgentCalling(ua.getCallee());
        }
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
                callQueueNotifyService.stopNotifyAnswerTimeout(caller, null);
            }
            redisQueueService.removeCacheAgentCalling(ua.getCallee());
        }
    }

    public void sendMessage(Message message) {
        try {
//            Stanza xmlString  = (Stanza) PacketParserUtils.getParserFor(String.valueOf(message));
            XmlStringBuilder stringBuilder = (XmlStringBuilder) message.toXML();
            kafkaTemplate.send(kafka_topic, stringBuilder.toString());
            logger.info("push2Queue|Message|" + stringBuilder);
        } catch (Exception e) {
            logger.error("push2Queue|Exception|" + e.getMessage(), e);
        }
    }

    private String getSdp(SipMessageEvent event, String originCallId) {
        String sdp = event.getMessage().getRawContent().toString();
        if (sdp.contains("m=video ") && originCallId != null) {
            if (!redisQueueSessionService.getCacheVideoCallBySessionId(originCallId)) {
                sdp = sdp.split("m=video ")[0];
            }
        }
        return sdp;
    }

    public Message makeXmppMessageCallinInvite(String caller,
                                               String callerName,
                                               String originCallId,
                                               String callee,
                                               String to,
                                               String sdp,
                                               String callID,
                                               String additionaldata,
                                               boolean isAppToApp,
                                               boolean isRob) {
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
                    .addAttribute("originCallId", originCallId)
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

                if (originCallId != null) {

                    JsonObject jsonObject = gson.fromJson(additionaldata, JsonObject.class);

                    CallCDR callCDR = redisQueueSessionService.getCacheCallCdrBySessionId(originCallId);

                    if (callCDR != null) {

                        jsonObject.addProperty("appId", callCDR.getCallerAppId());
                    }
                    Map<String, Object> queueInfo = redisQueueSessionService.getIvrQueueByCallId(originCallId);
                    if (!queueInfo.isEmpty()) {

                        jsonObject.addProperty("queueName", String.valueOf(queueInfo.get("queueName")));
                    }
                    extCalldataBuilder.addElement("additionaldata", gson.toJson(jsonObject));
                } else {

                    extCalldataBuilder.addElement("additionaldata", additionaldata);
                }
            }

            if (isAppToApp) {
                extCalldataBuilder.addElement("apptoapp", "1");
            }
            if (isRob) {
                extCalldataBuilder.addElement("rob", "1");
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

        callService.pushRedisNotify(calleeUsername, session, status, message);
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

