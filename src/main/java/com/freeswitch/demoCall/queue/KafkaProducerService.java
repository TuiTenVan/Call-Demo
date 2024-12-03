package com.freeswitch.demoCall.queue;

import com.freeswitch.demoCall.common.Key;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.sip.UserRegistry;
import com.freeswitch.demoCall.sip.UserSession;
import com.freeswitch.demoCall.utils.XmppStanzaUtil;
import com.google.gson.Gson;

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

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.UUID;

@Service
public class KafkaProducerService {
    private final Logger logger = LogManager.getLogger(KafkaProducerService.class);
    @Value("${ringme.kafka.topic.pstn2webrtc}")
    private String kafka_topic;

    private final String domainXmpp = "192.168.1.70";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private Gson gson;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    //    public void sendLogCallCdr(CDR cdr) {
//        try {
//            String callCdr = gson.toJson(cdr);
//            if(cdr.getType().equals("outbound-call")) {
//
//                kafkaTemplate.send(topicLogcallApp2Phone, callCdr);
//                logger.info("sendLogCallCdr|Topic={}|Message={}", topicLogcallApp2Phone, callCdr);
//            } else if (cdr.getType().equals("inbound-call")) {
//
//                kafkaTemplate.send(topicLogcallPhone2App, callCdr);
//                logger.info("sendLogCallCdr|Topic={}|Message={}", topicLogcallPhone2App, callCdr);
//            }
//        } catch (Exception e) {
//            logger.error("sendLogCallCdr|Exception|" + e.getMessage(), e);
//        }
//    }
    public void sendMessage(Message message) {
        try {
            XmlStringBuilder stringBuilder = (XmlStringBuilder) message.toXML();
            kafkaTemplate.send(kafka_topic, stringBuilder.toString());
            logger.info("push2Queue|Message|" + stringBuilder + Key.ENDLINE);
        } catch (Exception e) {
            logger.error("push2Queue|Exception|" + e.getMessage(), e);
        }
    }

    public void sendMessageResponse(UserSession userSession, Response response, String sdp, Long now) {
        try {

//            Stanza xmlString  = (Stanza) PacketParserUtils.getParserFor(String.valueOf(message));
            XmlStringBuilder stringBuilder = (XmlStringBuilder) makeXmlStrResponse(userSession, response, sdp, domainXmpp, now).toXML();
            kafkaTemplate.send(kafka_topic, stringBuilder.toString());
            logger.info("push2Queue|Message|" + stringBuilder + Key.ENDLINE);
        } catch (Exception e) {
            logger.error("push2Queue|Exception|" + e.getMessage(), e);
        }
    }

    public void sendMessageRequest(UserSession userSession, Request request, int code) {
        try {

            XmlStringBuilder stringBuilder = (XmlStringBuilder) makeXmlStrRequest(userSession, request, code, domainXmpp).toXML();
            kafkaTemplate.send(kafka_topic, stringBuilder.toString());
            logger.info("push2Queue|Message|" + stringBuilder + Key.ENDLINE);
        } catch (Exception e) {
            logger.error("push2Queue|Exception|" + e.getMessage(), e);
        }
    }


    public void sendError(String message) {

        logger.info("sendError|msg=" + message);
//        kafkaTemplate.send(kafka_topic, message);
    }

    private Message makeXmlStrRequest(UserSession userSession, Request request, int code_, String domain) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(XmppStanzaUtil.CALL_PSTN_JID + domain)
                    .to(userSession.getFrom())
                    .ofType(Message.Type.chat)
                    .setBody("callout")
                    .build();

            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callout")
                    .build();
            message.addExtension(extContentType);

            String code;
            if (code_ != 0) {
                code = String.valueOf(code_);
            } else if (request.getMethod().equals("BYE")) {
                code = "203";
            } else {
                code = "200";
            }
            StandardExtensionElement extData = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:callout:data")
                    .addAttribute("code", code)
                    .build();

            StandardExtensionElement extCalldata = StandardExtensionElement.builder(
                            "callout", "urn:xmpp:ringme:callout")
                    .addElement(extData)
                    .addElement("session", userSession.getSessionId())
                    .build();
            message.addExtension(extCalldata);

            StandardExtensionElement extNoStore = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();
            message.addExtension(extNoStore);
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    private Message makeXmlStrResponse(UserSession userSession, Response response, String sdp, String domain, Long now) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(XmppStanzaUtil.CALL_PSTN_JID + domain)
                    .to(userSession.getFrom())
                    .ofType(Message.Type.chat)
                    .setBody("callout")
                    .build();

            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callout")
                    .build();
            message.addExtension(extContentType);

            String code;
            boolean isHaveSdp = sdp != null && !sdp.isEmpty();
            if (response.getStatusCode() == 200 && isHaveSdp) {
                code = "202"; // ACCEPT
            } else {
                code = String.valueOf(response.getStatusCode());
            }
            StandardExtensionElement.Builder extDataBuilder = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:callout:data")
                    .addAttribute("code", code)
                    .setText(isHaveSdp ? sdp : "");

            if (response.getStatusCode() == 183) {

                String linkVideo = redisQueueSessionService.getCacheVideoRingBackBySessionId(userSession.getSessionId());
                if (linkVideo != null) {
                    extDataBuilder.addAttribute("link", linkVideo);
                    String isIvr = redisQueueSessionService.getCacheIsIvrBySessionId(userSession.getSessionId());
                    if (isIvr != null) {

                        extDataBuilder.addAttribute("isPlayIVR", isIvr);
                    }
                }
            }
            StandardExtensionElement extData = extDataBuilder.build();

            StandardExtensionElement extCalldata = StandardExtensionElement.builder(
                            "callout", "urn:xmpp:ringme:callout")
                    .addElement(extData)
                    .addElement("session", userSession.getSessionId())
                    .build();
            message.addExtension(extCalldata);

            StandardExtensionElement extNoStore = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();
            message.addExtension(extNoStore);

            StandardExtensionElement extOnReceived = StandardExtensionElement.builder(
                            "on-received", "urn:xmpp:debug")
                    .setText(String.valueOf(now))
                    .build();
            StandardExtensionElement extOnSent = StandardExtensionElement.builder(
                            "on-sent", "urn:xmpp:debug")
                    .setText(String.valueOf(System.currentTimeMillis()))
                    .build();
            message.addExtension(extOnReceived);
            message.addExtension(extOnSent);
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }
}
