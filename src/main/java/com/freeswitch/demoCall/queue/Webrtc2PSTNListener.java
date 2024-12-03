package com.freeswitch.demoCall.queue;

import com.freeswitch.demoCall.common.CDRKey;
import com.freeswitch.demoCall.common.CodecUtils;
import com.freeswitch.demoCall.config.Configuration;
import com.freeswitch.demoCall.entity.CalloutInfo;
import com.freeswitch.demoCall.mysql.dao.CallCDRDao;
import com.freeswitch.demoCall.mysql.entity.*;
import com.freeswitch.demoCall.mysql.dao.UserInfoDao;
import com.freeswitch.demoCall.mysql.entity.CallCDR;
import com.freeswitch.demoCall.mysql.entity.IVRHotline;
import com.freeswitch.demoCall.service.*;
import com.freeswitch.demoCall.service.callin.queue.CallQueueNotifyService;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callout.SipWebsocketManager;
import com.freeswitch.demoCall.service.callout.SipWebsocketService;
import com.freeswitch.demoCall.service.processor.StopCallProcessor;
import com.freeswitch.demoCall.sip.CallData;
import com.freeswitch.demoCall.sip.SipMessageBuilder;
import com.freeswitch.demoCall.sip.UserRegistry;
import com.freeswitch.demoCall.sip.UserSession;
import com.freeswitch.demoCall.utils.Utils;
import com.freeswitch.demoCall.utils.XmppStanzaUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.sip.message.Request;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Webrtc2PSTNListener {
    private final Logger logger = LogManager.getLogger(Webrtc2PSTNListener.class);

    private static final AtomicLong INDEX_VIETTEL = new AtomicLong();

    private static final AtomicLong INDEX_MOBI = new AtomicLong();

    private static final AtomicLong INDEX_VINA = new AtomicLong();

    @Value("${ringme.callout.prefered.codec}")
    private String preferedCodec;

//    @Value("${ringme.callout.sip.gw.address}")
//    private String sipGwAddress;

    @Autowired
    private Configuration configuration;

    @Value("${ringme.domain.xmpp}")
    private String domainXmpp;

    @Autowired
    private UserRegistry registry;

    @Autowired
    private Gson gson;

    @Autowired
    private SipWebsocketManager sipWebsocketManager;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private UserInfoDao userInfoDao;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private SdpProcessor sdpProcessor;

    @Autowired
    private CodeProcessor codeProcessor;

    @Autowired
    private StopCallProcessor stopCallProcessor;

    @Autowired
    private CallCDRDao callCDRDao;

    @Autowired
    private UserService userService;

    @Autowired
    private CallQueueNotifyService callQueueNotifyService;

    @Autowired
    private CallService callService;

    @KafkaListener(id = "webrtc_1",
            topics = "${ringme.kafka.topic.webrtc2pstn}",
            autoStartup = "true", groupId = "${ringme.kafka.group.webrtc2pstn}")
    public void onMessage(String message) {

        logger.info("RECEIVE: {}\r\n", message);
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(new ByteArrayInputStream(message.getBytes()));
            String from1 = getAttribute(document, "message", "from");
            assert from1 != null;
            if (!(from1.contains("i0e41vqnbo1726800853460") || from1.contains("v95n4bbm741726800949014"))) {
                return;
            }
            if (Objects.equals(getAttribute(document, "message", "type"), "error")) {
                String callId = getTagValue(document, "session");
                String type = getAttribute(document, "contentType", "name");

                if (type != null && type.equals("callout")) {

                    final String PREFIX_LOG_CALLOUT = "CALLOUT|" + callId + "|";
                    String code = getAttribute(document, "data", "code");
                    String text = getTagValue(document, "text");
                    logger.info(PREFIX_LOG_CALLOUT + "|" + code + "|" + text);

//                    if ("User session not found".equals(text) && code != null && (code.equals("202") || code.equals("183"))) {
//
//                        UserSession userSession = registry.getBySessionId(callId);
//
//                        if (userSession == null) {
//
//                            logger.info("CALLOUT|" + callId + "|UserSession is null => cannot handle error code: {}", code);
//                            // handle remake in class ResetAppService
//                        } else {
//                            logger.info("CALLOUT|" + userSession.getCaller() + "|" +
//                                    userSession.getCallee() + "|" + callId + "|PROCESS_ERROR_CODE=" + code);
//                            CalloutInfo job = CalloutInfo.builder()
//                                    .fullMsg(message)
//                                    .serviceType(Integer.parseInt(code))
//                                    .callId(callId)
//                                    .caller(userSession.getCaller())
//                                    .callee(userSession.getCallee())
//                                    .errorCode(486)
//                                    .cst(0)
//                                    .build();
//
////                            String time = getTagValue(document, "time");
//                            doJobCallout(job, userSession);
//                        }
//                    }
                }
                return;
            }
            if (!(hasSubTag(document, "callout") ||
                    hasSubTag(document, "callin") ||
                    hasSubTag(document, "callivr"))) {
                logger.info("Not valid callout stanza");
                return;
            }
//            Node node = document.getElementsByTagName("callout").item(0);
            String callId = getTagValue(document, "session");
            String code = getAttribute(document, "data", "code");

            if (hasSubTag(document, "callout")) {
                switch (Objects.requireNonNull(code)) {
                    case "100": //** INVITE **
                        long timeInvite = System.currentTimeMillis();

                        // code 100 = INVITE callout
                        String from = getAttribute(document, "message", "from");
                        String usernameCaller = from.split("@")[0];
                        String to = getAttribute(document, "message", "to");
                        String caller_origin = getTagValue(document, "caller");
                        String callee_origin = Utils.validatePhone(getTagValue(document, "callee"), "VN");

                        final String PREFIX_LOG_CALLOUT = "CALLOUT|" + caller_origin + "|" + callee_origin + "|" + callId + "|";
                        logger.info(PREFIX_LOG_CALLOUT + "PROCESS CALLOUT 100 INVITE");

                        String timeXmpp = getAttribute(document, "xmpp-in-queue", "value");
                        if(timeXmpp != null) {
                            long timeXmppInvite = Long.parseLong(timeXmpp);
                            if(timeInvite - timeXmppInvite > 30000) {
                                logger.info(PREFIX_LOG_CALLOUT + "INVITE XMPP TIMEOUT (DELTA = 30S)|{}", timeInvite);
                                kafkaProducerService.sendMessage(XmppStanzaUtil
                                        .makeXmlUserNotValid(from, callId,
                                                499, domainXmpp));
                                createErrorCdrCalloutBySystem(callId, from.split("\\/")[0], caller_origin, callee_origin,
                                        null, null, null, null,
                                        timeInvite,
                                        "XMPP_INVITE_TIMEOUT", 499);
                                break;
                            }
                        }

                        if (callee_origin == null) {
                            logger.info(PREFIX_LOG_CALLOUT + "CALLEE_INVALID|Cannot validatePhone={}", getTagValue(document, "callee"));
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            499, domainXmpp));
                            createErrorCdrCalloutBySystem(callId, from.split("\\/")[0], caller_origin, callee_origin,
                                    null, null, null, null,
                                    timeInvite,
                                    "CALLEE_INVALID", 499);
                            break;
                        }

                        UserInfo userInfo = userInfoDao.getUserInfoByUsername(usernameCaller);
                        if (userInfo == null) {
                            logger.info(PREFIX_LOG_CALLOUT + usernameCaller + "|CALLER NOT EXIST USER INFO: {}", caller_origin);
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            499, domainXmpp));
                            createErrorCdrCalloutBySystem(callId, from.split("\\/")[0], caller_origin, callee_origin,
                                    null, null, null, null,
                                    timeInvite,
                                    "CALLER_INVALID", 499);
                            break;
                        }

                        if (userInfo.getEnable_callout() == null || userInfo.getEnable_callout() == 0 || userInfo.getRemain_call_charge() < 1) {
                            int statusCode = (userInfo.getEnable_callout() == null || userInfo.getEnable_callout() == 0) ? 496 : 497;
                            logger.info(PREFIX_LOG_CALLOUT + "CALLER NOT ACCEPT CALLOUT: {}|{}", caller_origin, statusCode);


//                            if(!bridgeToIVRCancel(PREFIX_LOG_CALLOUT, document, callId, statusCode, userInfo)) {
//                                kafkaProducerService.sendMessage(XmppStanzaUtil
//                                        .makeXmlUserNotValid(from.split("\\/")[0], callId,
//                                                statusCode, domainXmpp));
//                            }
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            statusCode, domainXmpp));
                            createErrorCdrCalloutBySystem(callId, from.split("\\/")[0], caller_origin, callee_origin,
                                    userInfo, null, null, null,
                                    timeInvite,
                                    "Error", statusCode);
                            break;
                        }

                        if (redisService.checkCallingForCaller(caller_origin)) {
                            logger.info(PREFIX_LOG_CALLOUT + "CALLER IN CALLING: {}", caller_origin);
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            498, domainXmpp));
                            //** NOTE: chỉ gửi tới resouce gửi bản tin invite này lên **
                            createErrorCdrCalloutBySystem(callId, from.split("\\/")[0], caller_origin, callee_origin,
                                    userInfo, null, null, null,
                                    timeInvite,
                                    "CALLER_IN_CALLING", 498);
//                            redisService.removeCallingForCaller(caller_origin);
                            break;
                        }


                        String data = getTagValue(document, "data");
                        Map<String, Object> mapData = new HashMap<>();
                        mapData.put("type", 2);
                        mapData.put("sdp", data);
                        data = gson.toJson(mapData);
//                        String caller = null;
//                        if (mnpMap.get("mnpFrom").equals("viettel")) {
//
//                            caller = configuration.getSipHotlineViettelList()[getHotlineViettelIndex()];
//                        } else if (mnpMap.get("mnpFrom").equals("mobiphone")) {
//
//                            caller = configuration.getSipHotlineMobiList()[getHotlineMobiIndex()];
//                        } else if (mnpMap.get("mnpFrom").equals("vinaphone")) {
//
//                            caller = configuration.getSipHotlineVinaList()[getHotlineVinaIndex()];
//                        }

                        UserSession userSession = UserSession.builder()
                                .from(from)
                                .to(to.split("\\/")[0])
                                .caller(caller_origin)
                                .calleeName(getAttribute(document, "callee", "name"))
                                .callee(callee_origin)
                                .sessionId(callId)
                                .isSendSdp(false)
                                .timeRemain(userInfo.getRemain_call_charge() != null ? userInfo.getRemain_call_charge() : 0L)
                                .build();

                        String platform = getTagValue(document, "platform");
                        String version = getTagValue(document, "version");

                        CallCDR callCDR = CallCDR.builder()
                                .sessionId(userSession.getSessionId())
                                .callerUsername(userSession.getFrom().split("@")[0])
                                .callerPhoneNumber(userSession.getCaller())
                                .calleePhoneNumber(userSession.getCallee())
                                .callerIdDepartment(userInfo.getIdDepartment())
                                .callerIdProvince(userInfo.getIdProvince())
                                .callerAppId(userInfo.getAppId())
                                .callerType(userInfo.getType())
                                .callerPosition(userInfo.getPosition())
                                .callType("callout")
                                .callStatus("invite")
                                .createdAt(new Date(timeInvite))
                                .ownerId(userInfo.getOwnerId())
                                .build();

                        callCDR.setNetworkType(callCDR.getMnpFrom().equals(callCDR.getMnpTo()) ? "on-net" : "off-net");
                        String additionaldata = getTagValue(document, "additionaldata");
                        if (!additionaldata.isEmpty()) {

                            callCDR.setAdditionalData(additionaldata);

                            JsonObject jsonObject = gson.fromJson(additionaldata, JsonObject.class);
                            JsonElement jsonElement = jsonObject.get("order_info");

                            if (jsonElement != null &&
                                    !jsonElement.getAsJsonObject().isJsonNull() &&
                                    !jsonElement.getAsJsonObject().isEmpty() &&
                                    jsonElement.getAsJsonObject().get("id") != null) {
                                callCDR.setOrderId(jsonElement.getAsJsonObject().get("id").getAsString());
                            }
                        }

                        break;
                    case "101":  //** PING CHECK CALLING **

                        String state = getAttribute(document, "data", "type");

                        if (state != null && state.equals("1")) {

                            remakeUserSession(callId);
                        } else {
                            logger.info("CALLOUT|{}|not accepted", callId);
                        }
                        break;
                    case "201":
                        logger.info("CALLOUT|" + callId + "|200|XMPP connected");
                        break;
                    case "500":
                        logger.info("CALLOUT|" + callId + "|500|XMPP connect fail");
                        // TODO: reject call
                        break;
                    case "203":
                    case "486": //** BYE/CANCEL **
                        UserSession userSession2 = registry.getBySessionId(callId);

                        if (userSession2 == null) {

                            logger.info("CALLOUT|" + callId + "|UserSession is null => cannot handle code: {}", code);
                            // handle remake in class ResetAppService
                        } else {
                            logger.info("CALLOUT|" + userSession2.getCaller() + "|" +
                                    userSession2.getCallee() + "|" + callId + "|" + code);
                            CalloutInfo job = CalloutInfo.builder()
                                    .fullMsg(message)
                                    .serviceType(Integer.parseInt(code))
                                    .callId(callId)
                                    .caller(userSession2.getCaller())
                                    .callee(userSession2.getCallee())
                                    .errorCode(Integer.parseInt(code))
                                    .cst(0)
                                    .build();

//                            String time = getTagValue(document, "time");
                            doJobCallout(job, userSession2);
                        }
                        break;
                    default:
                        break;
                }
            } else if (hasSubTag(document, "callin")) {
                final String PREFIX_LOG_CALLIN = "CALLIN|" + callId;
                switch (Objects.requireNonNull(code)) {
                    case "180":
                    case "202": //** RINGING/ACCEPT **

                        logger.info(PREFIX_LOG_CALLIN + "|PROCESS CODE {}", code);
                        String answerSdp = getTagValue(document, "data");

                        CalloutInfo callinJob = CalloutInfo.builder()
                                .fullMsg(message)
                                .serviceType(Integer.parseInt(code))
                                .callId(callId)
                                .caller(getTagValue(document, "caller"))
                                .callee(getTagValue(document, "callee"))
                                .errorCode(Integer.parseInt(code))
                                .data(answerSdp)
                                .cst(0)
                                .build();

                        doJobCallin(callinJob);
                        break;
                    case "201":
                        logger.info("CALLIN|" + callId + "|200|XMPP connected");
                        break;
                    case "500":
                        logger.info("CALLIN|" + callId + "|500|XMPP connect fail");
                        // TODO: reject call
                        break;
                    default:
                        logger.info(PREFIX_LOG_CALLIN + "|PROCESS CODE {}", code);
                        CalloutInfo callinJob2 = CalloutInfo.builder()
                                .fullMsg(message)
                                .serviceType(Integer.parseInt(code))
                                .callId(callId)
                                .errorCode(Integer.parseInt(code))
                                .cst(0)
                                .build();

                        doJobCallin(callinJob2);
                        break;
                }
            } else if (hasSubTag(document, "callivr")) {
                switch (Objects.requireNonNull(code)) {
                    case "100": //** INVITE **
                        long timeInvite = System.currentTimeMillis();

                        // code 100 = INVITE callout
                        String from = getAttribute(document, "message", "from");
                        String usernameCaller = from.split("@")[0];
                        String to = getAttribute(document, "message", "to");

                        String caller_origin = getTagValue(document, "caller");
                        String callee_origin = getTagValue(document, "callee");

                        final String PREFIX_LOG_CALLOUT = "CALL_IVR|" + caller_origin + "|" + callee_origin + "|" + callId + "|";
                        logger.info(PREFIX_LOG_CALLOUT + "PROCESS CALL_IVR 100 INVITE");

                        String timeXmpp = getAttribute(document, "xmpp-in-queue", "value");
                        if(timeXmpp != null) {
                            long timeXmppInvite = Long.parseLong(timeXmpp);
                            if(timeInvite - timeXmppInvite > 30000) {
                                logger.info(PREFIX_LOG_CALLOUT + "INVITE XMPP TIMEOUT (DELTA = 30S)|{}", timeInvite);
                                kafkaProducerService.sendMessage(XmppStanzaUtil
                                        .makeXmlUserNotValid(from, callId,
                                                499, domainXmpp));
                                break;
                            }
                        }
                        UserInfo userInfo = userInfoDao.getUserInfoByUsername(usernameCaller);
                        logger.info(PREFIX_LOG_CALLOUT + "userInfo: {}", userInfo); // anonymous ko co userinfo
//                        if (userInfo == null) {
//                            logger.info(PREFIX_LOG_CALLOUT + usernameCaller + "|CALLER NOT EXIST USER INFO: {}", caller_origin);
//                            kafkaProducerService.sendMessage(XmppStanzaUtil
//                                    .makeXmlUserNotValid(from, callId,
//                                            499, domainXmpp));
//                            break;
//                        }

                        String data = getTagValue(document, "data");
                        Map<String, Object> mapData = new HashMap<>();
                        mapData.put("type", 2);
                        mapData.put("sdp", data);
                        data = gson.toJson(mapData);

                        CallData callData = new Gson().fromJson(data, CallData.class);
                        String newSdpData = CodecUtils.preferCodec(callData.getSdp(), preferedCodec, true);
                        callData.setSdp(newSdpData);

                        logger.info(PREFIX_LOG_CALLOUT + "MAKE SDP INVITE SUCCESS");

                        String prefixSipUri = "sip:";
                        String sipSchema = "SIP";
                        Map<String, Object> map = sipWebsocketManager.getSipWebsocketService();

                        if(map == null) {
                            logger.info(PREFIX_LOG_CALLOUT + "MAP SIP WEBSOCKET IS EMPTY");
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            499, domainXmpp));
                            break;
                        }
                        logger.info(PREFIX_LOG_CALLOUT + "GET FS WEBSOCKET SUCCESS|" + map.get("id") + "|" + map.get("address"));

                        String callee = callee_origin;

                        String agentAssign = null;
                        String agentPhoneNumber = null;
                        UserInfo agentInfo = null;
                        boolean isVip = userInfo != null &&
                                userInfo.getEnableVip() == 1 && userInfo.getAgentNameSupport() != null;

                        if (isVip) {
                            agentInfo = userInfoDao.getUserInfoByUsername(userInfo.getAgentNameSupport());
                            if (agentInfo != null) {

                                callee = callee + "_vip_" + agentInfo.getPhoneNumber() + "_" + userInfo.getAgentNameSupport();
                                agentAssign = userInfo.getAgentNameSupport();
                                agentPhoneNumber = agentInfo.getPhoneNumber();
                            }
                        }
                        String sipGwAddress = String.valueOf(map.get("address"));
                        String calleeSIPURI = prefixSipUri + callee + "@" + sipGwAddress;
                        String callerSIPURI = sipSchema + ":" + caller_origin + "@" + sipGwAddress + "?X-sId=" + callId;
                        SIPRequest request = null;
                        try {

                            request = (SIPRequest) SipMessageBuilder
                                    .buildInviteRequest(caller_origin, callerSIPURI, "WS", calleeSIPURI, callData.getSdp(), callId, sipGwAddress);
                        } catch (Exception ex) {
                            logger.error(ex.getMessage(), ex);
                        }

                        if (request == null) {
                            logger.info(PREFIX_LOG_CALLOUT + "CANNOT MAKE SIP MESAGE INVITE REQUEST");
                            kafkaProducerService.sendMessage(XmppStanzaUtil
                                    .makeXmlUserNotValid(from, callId,
                                            499, domainXmpp));
                            break;
                        }
                        UserSession userSession = UserSession.builder()
                                .from(from)
                                .to(to.split("\\/")[0])
                                .caller(caller_origin)
                                .calleeName(getAttribute(document, "callee", "name"))
                                .callee(callee_origin)
                                .sessionId(callId)
                                .isSendSdp(false)
                                .requestInvite(request)
                                .sipWebsocketId(String.valueOf(map.get("id")))
                                .timeRemain(5 * 60)
                                .build();



                        break;
                    case "203":
                    case "486": //** BYE/CANCEL **
                        UserSession userSession2 = registry.getBySessionId(callId);

                        if (userSession2 == null) {

                            logger.info("CALL_IVR|" + callId + "|UserSession is null => cannot handle code: {}", code);
                            // handle remake in class ResetAppService
                        } else {
                            logger.info("CALL_IVR|" + userSession2.getCaller() + "|" +
                                    userSession2.getCallee() + "|" + callId + "|" + code);
                            CalloutInfo job = CalloutInfo.builder()
                                    .fullMsg(message)
                                    .serviceType(Integer.parseInt(code))
                                    .callId(callId)
                                    .caller(userSession2.getCaller())
                                    .callee(userSession2.getCallee())
                                    .errorCode(Integer.parseInt(code))
                                    .cst(0)
                                    .build();

//                            String time = getTagValue(document, "time");
                            doJobCallout(job, userSession2);
                            if (code.equals("486")) {
                                callQueueNotifyService.stopNotifyWaitTimeout(userSession2.getCaller(), callId);
                            }
                        }
                        break;
                    case "DTMF":
                        UserSession userSession3 = registry.getBySessionId(callId);
                        if (userSession3 == null) {

                            logger.info("CALL_IVR|" + callId + "|UserSession is null => cannot handle code: {}", code);
                            // handle remake in class ResetAppService
                        } else {

                            logger.info(userSession3 + "|" + userSession3.getSipWebsocketId());
                            try {

                                Request request2 = SipMessageBuilder
                                        .buildInfoRequest(userSession3.getRequestInvite(), getTagValue(document, "data"));

                                sipWebsocketManager
                                        .getSipWebsocketServiceThreadById(userSession3.getSipWebsocketId()).send(request2);
                            } catch (Exception ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    private void remakeUserSession(String sessionId) {
        String rKey = redisService.getPrefixKeyWithInstanceId() + sessionId;
        Map<Object, Object> entries = redisService.getRecentCallout(rKey);
        UserSession userSession = UserSession.builder()
                .from(String.valueOf(entries.get(CDRKey.OTT_USERNAME)))
                .to(XmppStanzaUtil.CALL_PSTN_JID + domainXmpp)
                .caller(String.valueOf(entries.get(CDRKey.CALLER)))
                .callee(String.valueOf(entries.get(CDRKey.CALLEE)))
                .sessionId(sessionId)
                .isSendSdp(false)
                .requestInvite((SIPRequest) entries.get(CDRKey.SIP_REQUEST))
                .response((SIPResponse) entries.get(CDRKey.SIP_RESPONSE))
                .sipWebsocketId(String.valueOf(entries.get(CDRKey.SIP_WEBSOCKET_ID)))
                .timeRemain(Long.parseLong(String.valueOf(entries.get(CDRKey.TIME_REMAIN))))
                .build();

        Object object = entries.get(CDRKey.SIP_RESPONSE);
        if (!Objects.isNull(object)) {
            userSession.setResponse((SIPResponse) object);
        }
        logger.info("CALLOUT|{}|remakeUserSession", sessionId);
        registry.register(userSession);

        String timeStartStr = redisService.getTimeAcceptStrFromRedis(sessionId);
        if (timeStartStr != null) {
            long timeStart = Long.parseLong(timeStartStr);
            long remain = userSession.getTimeRemain() * 1000; // millisecond time remain

            if (System.currentTimeMillis() - timeStart < remain) {

                long remainTimeToOCS = remain - (System.currentTimeMillis() - timeStart);
                logger.info("CALLOUT|{}|remake ocs|{}", sessionId, remainTimeToOCS / 1000);
                redisService.rePushCallout(sessionId);
            } else {
                logger.info("CALLOUT|{}|drop call ocs", sessionId);
            }
        } else {
            logger.info("CALLOUT|{}|NOT_ACCEPT|not remake ocs", sessionId);
        }
    }
    private boolean bridgeToIVRCancel(String prefixLog, Document document, String callId, int statusCode, UserInfo userInfo) {
        String caller = Utils.validatePhone(getTagValue(document, "caller"), "VN");
        String data = getTagValue(document, "data");
        Map<String, Object> mapData = new HashMap<>();
        mapData.put("type", 2);
        mapData.put("sdp", data);
        data = gson.toJson(mapData);

        CallData callData = new Gson().fromJson(data, CallData.class);
        String newSdpData = CodecUtils.preferCodec(callData.getSdp(), preferedCodec, true);
        callData.setSdp(newSdpData);

        String prefixSipUri = "sip:";
        String sipSchema = "SIP";
        Map<String, Object> map = sipWebsocketManager.getSipWebsocketService();

        if(map == null) {
            return false;
        }

        String sipGwAddress = String.valueOf(map.get("address"));
        String ivrPath;
        if(statusCode == 496) {

            if(userInfo.getIdProvince().equals("10")) {
                ivrPath = "not_enable_callout";
            } else {
                ivrPath = "not_in_demopoc";
            }
        } else if (statusCode == 497) {
            if(userInfo.getIdProvince().equals("10")) {
                ivrPath = "not_remain_charge";
            } else {
                ivrPath = "not_in_demopoc";
            }
        } else {
            ivrPath = "not_enable_callout";
        }

        ivrPath = "ringme_ivr_cancel_" + ivrPath;
        String calleeSIPURI = prefixSipUri + ivrPath + "@" + sipGwAddress;
        String callerSIPURI = sipSchema + ":" + caller + "@" + sipGwAddress + "?X-sId=" + callId;

        SIPRequest request;
        try {

            request = (SIPRequest) SipMessageBuilder
                    .buildInviteRequest(caller, callerSIPURI, "WS", calleeSIPURI, callData.getSdp(), callId, sipGwAddress);

            UserSession userSession = UserSession.builder()
                    .sessionId(callId)
                    .from(getAttribute(document, "message", "from"))
                    .requestInvite(request)
                    .caller(userInfo.getPhoneNumber())
                    .callee("ivr_cancel")
                    .sipWebsocketId(String.valueOf(map.get("id")))
                    .build();
            registry.register(userSession);
            SipWebsocketService sipWebsocketService = (SipWebsocketService) map.get("ws");
            sipWebsocketService.send(request);
            logger.info(prefixLog + "PLAY_IVR_CANCEL|" + ivrPath);
            return true;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return false;
    }
    private void createErrorCdrCalloutBySystem(String sessionId,
                                               String callerUsername,
                                               String callerPhoneNumber,
                                               String calleePhoneNumber,
                                               UserInfo userInfo,
                                               String hotline,
                                               String mnpFrom,
                                               String mnpTo,
                                               long timeInvite,
                                               String callStatus, Integer callStatusCode) {
        try {

            CallCDR callCDR = CallCDR.builder()
                    .sessionId(sessionId)
                    .callerUsername(callerUsername.split("@")[0])
                    .callerPhoneNumber(callerPhoneNumber)
                    .calleePhoneNumber(calleePhoneNumber)
                    .callType("callout")
                    .hotline(hotline)
                    .mnpFrom(mnpFrom)
                    .mnpTo(mnpTo)
                    .createdAt(new Date(timeInvite))
                    .ownerId(userInfo.getOwnerId())
                    .build();

            if (userInfo != null) {

                callCDR.setCallerIdDepartment(userInfo.getIdDepartment());
                callCDR.setCallerIdProvince(userInfo.getIdProvince());
                callCDR.setCallerAppId(userInfo.getAppId());
                callCDR.setCallerType(userInfo.getType());
                callCDR.setCallerPosition(userInfo.getPosition());
            }
            callCDR.setCallStatus(callStatus);
            callCDR.setCallStatusCode(callStatusCode);
            callCDR.setEndTime(new Date());

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }


    private void doJobCallout(CalloutInfo job, UserSession userSession) throws IOException {

        SipWebsocketService sipWebsocketService = sipWebsocketManager.getSipWebsocketServiceThreadById(userSession.getSipWebsocketId());

        if (sipWebsocketService == null) {
            logger.info("CALLOUT|" + userSession.getSessionId() + "|sipWebsocketService IS NULL|" + userSession.getSipWebsocketId());
            registry.removeBySessionId(userSession.getSessionId());
            redisService.removeCallingForCaller(userSession.getCaller());
        } else {

            switch (job.getErrorCode()) {
                case 203:

                    registry.removeBySessionId(userSession.getSessionId());
                    redisService.removeCallingForCaller(userSession.getCaller());

                    Request requestBye = SipMessageBuilder.buildByeRequest(userSession.getRequestInvite(), userSession.getResponse());

                    sipWebsocketService.send(requestBye);

                    break;
                case 486:
                    registry.removeBySessionId(userSession.getSessionId());
                    redisService.removeCallingForCaller(userSession.getCaller());

                    if(userSession.isSendSdp()) {

                        logger.info("Send bye for cancel | call session is accepted");
                        Request requestBye2 = SipMessageBuilder.buildByeRequest(userSession.getRequestInvite(), userSession.getResponse());
                        sipWebsocketService.send(requestBye2);
                    } else {

                        Request requestCancel = SipMessageBuilder.buildCancelRequest(userSession.getRequestInvite());
                        sipWebsocketService.send(requestCancel);
                    }

                    break;
                default:
                    logger.error("Could not found processor for msg: serviceType=" + job.getServiceType() + " | body="
                            + job.getFullMsg());
                    break;
            }
        }
    }

    private void doJobCallin(CalloutInfo job) throws IOException {
        switch (job.getErrorCode()) {
            case 180:
            case 200:
            case 486:
            case 487:
            case 488:
            case 489:
                codeProcessor.process(job);
                break;
            case 202:
                sdpProcessor.process(job);
                break;
            case 203:
                redisService.putRecentCallinStatusCode(job.getCallId(), job.getErrorCode());
                stopCallProcessor.process(job);
                break;
            default:
                logger.error("Could not found processor for msg: serviceType=" + job.getServiceType() + " | body="
                        + job.getFullMsg());
                break;
        }
    }

    private String getTagValue(Document parse, String name) {
        String result = "";
        NodeList nodeList = parse.getElementsByTagName(name);
        if (nodeList.getLength() == 0) {
            return "";
        }
        if (nodeList.item(0).getFirstChild() == null) {
            return "";
        }
        result = nodeList.item(0).getFirstChild().getNodeValue();
        return result != null ? result : "";
    }

    private boolean hasSubTag(Document parse, String tagName) {
        NodeList nodeList = parse.getElementsByTagName(tagName);
        return (nodeList != null && nodeList.getLength() > 0);
    }

    private String getAttribute(Document parse, String tagName, String attrName) {
        NodeList nodeList = parse.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        Element e = (Element) nodeList.item(0);
        return e.getAttribute(attrName);
    }

}
