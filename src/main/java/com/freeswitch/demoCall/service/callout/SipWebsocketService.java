package com.freeswitch.demoCall.service.callout;

import com.freeswitch.demoCall.queue.KafkaProducerService;
import com.freeswitch.demoCall.service.RedisService;
import com.freeswitch.demoCall.sip.SipMessageBuilder;
import com.freeswitch.demoCall.sip.SipMessageHelper;
import com.freeswitch.demoCall.sip.UserRegistry;
import com.freeswitch.demoCall.sip.UserSession;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class SipWebsocketService extends TextWebSocketHandler {
    private final Logger logger = LogManager.getLogger(SipWebsocketService.class);

    //    private WebSocketClient webSocketClient;
    private final UserRegistry registry;

    //    private Caffeine<String, Object> caffeineCache;
    private final KafkaProducerService kafkaProducerService;
    private final String fsWebSocketAddress;

    private final RedisService redisService;

    private final String freeswitchIp;

    private WebSocketSession session;

    public SipWebsocketService(String fsWebSocketAddress, ApplicationContext context) {
        this.fsWebSocketAddress = fsWebSocketAddress;
        this.kafkaProducerService = context.getBean(KafkaProducerService.class);
        this.registry = context.getBean(UserRegistry.class);
        this.redisService = context.getBean(RedisService.class);
        this.freeswitchIp = fsWebSocketAddress.replace("ws://", "")
                .replace("wss://", "").split(":")[0];
        openSessionConnection();
    }

    public String getWebsocketSessionId() {
        return this.session.getId();
    }

    private void openSessionConnection() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        try {
            ListenableFuture<WebSocketSession> sessionListenableFuture = webSocketClient.doHandshake(this, new WebSocketHttpHeaders(), new URI(this.fsWebSocketAddress));
            session = sessionListenableFuture.get();
            logger.info("FINISH INIT WEB-SOCKET CONNECTION: {} - {}", session.getId(), session.isOpen());
        } catch (InterruptedException | ExecutionException | URISyntaxException e) {
            logger.info("CANNOT INIT WEB-SOCKET CONNECTION: {}", fsWebSocketAddress);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long now = System.currentTimeMillis();

        String payload = message.getPayload();
        if (payload.startsWith("SIP/2.0")) {
            SIPResponse response = (SIPResponse) SipMessageHelper.messageFactory.createResponse(payload);
            if (!response.getCSeq().getMethod().equals("OPTIONS")) {
                logger.info("{} ({}) RESPONSE:\n{}", fsWebSocketAddress, session.getId(), response);
                String callId = response.getHeader("Call-ID").toString().replace("Call-ID: ", "").trim();

//                if(response.getTo().toString().contains("ringme_ivr_cancel_")) {
//                    sendResponseCancelIvr(response, callId);
//                    return;
//                }
                UserSession userSession = registry.getBySessionId(callId);
                if (userSession != null) {
                    final String PREFIX_LOG_CALLOUT = "CALLOUT|" +
                            userSession.getCaller() + "|" +
                            userSession.getCallee() + "|" + userSession.getSessionId() + "|";
                    String sdpContent = "";
                    ContentTypeHeader contentType = (ContentTypeHeader) response.getHeader(ContentTypeHeader.NAME);
                    ContentLengthHeader contentLen = (ContentLengthHeader) response.getHeader(ContentLengthHeader.NAME);
                    if (contentLen.getContentLength() > 0 && contentType.getContentSubType().equals("sdp")) {
                        String charset = contentType.getParameter("charset");
                        if (charset == null)
                            charset = "UTF-8"; // RFC 3261

                        sdpContent = new String(response.getRawContent(), charset);
                    }
                    if (response.getStatusCode() == 200 && userSession.isSendSdp() && !sdpContent.isEmpty()) {
                        logger.info(PREFIX_LOG_CALLOUT + "SESSION_SENT_INVITE");
                    } else {

                        kafkaProducerService.sendMessageResponse(userSession, response, sdpContent, now);
                        logger.info(PREFIX_LOG_CALLOUT + "SEND RESPONSE TO XMPP|" + response.getStatusCode());
                        if (response.getStatusCode() == 180) {
                            logger.info(PREFIX_LOG_CALLOUT + "SEND RINGING TO XMPP|" + response.getStatusCode());
                        } else if (response.getStatusCode() == 183) {
                            logger.info(PREFIX_LOG_CALLOUT + "SEND PRE_ANSWER TO XMPP|" + response.getStatusCode());
                            userSession.setResponse(response);
                        } else if (response.getStatusCode() == 200 && !sdpContent.isEmpty()) {

                            userSession.setSendSdp(true);
                            userSession.setResponse(response);
                            Request requestAck = SipMessageBuilder
                                    .buildAckRequest(userSession.getRequestInvite(), response);
                            send(requestAck);

                            if (!userSession.isIvr()) {

                                redisService.updateCDRAcceptToRedis(userSession.getSessionId(), now);
//                            registry.register(userSession);
                                redisService.updateResponseSipToRedis(userSession.getSessionId(), response);
                            }
                        } else if (response.getStatusCode() >= 400) {

                            Request requestAck = SipMessageBuilder
                                    .buildAckRequest(userSession.getRequestInvite(), response);
                            send(requestAck);

                            registry.removeBySessionId(userSession.getSessionId());

                            if (!userSession.isIvr()) {
                                redisService.removeCallingForCaller(userSession.getCaller());
                            }
                        }
                    }
                } else {
                    logger.info("USER SESSION IS NULL|" + callId + "|" + response.getStatusCode());
                }
            }
        } else {
            Request request = SipMessageHelper.messageFactory.createRequest(payload);

            UserSession userSession = registry.getBySessionId(request.getHeader("Call-ID").toString().replace("Call-ID: ", "").trim());

            logger.info("REQUEST: {}\r\n", request);
            if (userSession != null) {
                final String PREFIX_LOG_CALLOUT = "CALLOUT|" +
                        userSession.getCaller() + "|" +
                        userSession.getCallee() + "|";
                logger.info(PREFIX_LOG_CALLOUT + "SEND REQUEST TO XMPP|" + request.getMethod());
                kafkaProducerService.sendMessageRequest(userSession, request, 0);
                if (request.getMethod().equals("BYE")) {

                    sendResponse(SipMessageBuilder.build200OkResponse(request));
                    if(userSession.getCallee().equals("ivr_cancel")) {

                        logger.info("BYE|ivr_cancel");
                        registry.removeBySessionId(userSession.getSessionId());
                    } else {

                        handleByeRequest(userSession);
                    }
                }
            }
        }
    }


    private void sendResponseCancelIvr(SIPResponse response, String callId) {

        if(response.getCSeq().getMethod().equals("INVITE")) {

            logger.info("sendResponseCancelIvr|" + callId);
            UserSession userSession = registry.getBySessionId(callId);
            if(userSession != null) {
                userSession.setResponse(response);
                Request requestAck = SipMessageBuilder
                        .buildAckRequest(userSession.getRequestInvite(), response);
                try {
                    send(requestAck);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                String sdpContent = "";
                ContentTypeHeader contentType = (ContentTypeHeader) response.getHeader(ContentTypeHeader.NAME);
                ContentLengthHeader contentLen = (ContentLengthHeader) response.getHeader(ContentLengthHeader.NAME);
                if (contentLen.getContentLength() > 0 && contentType.getContentSubType().equals("sdp")) {
                    String charset = contentType.getParameter("charset");
                    if (charset == null)
                        charset = "UTF-8"; // RFC 3261

                    try {
                        sdpContent = new String(response.getRawContent(), charset);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }

                kafkaProducerService.sendMessageResponse(userSession,
                        response,
                        sdpContent,
                        System.currentTimeMillis());
            } else {
                logger.info("sendResponseCancelIvr|USER SESSION IS NULL|" + callId);
            }
        }
    }
    private void handleByeRequest(UserSession userSession) {


        if (!userSession.isIvr()) {
            redisService.removeCallingForCaller(userSession.getCaller());
        }
        registry.removeBySessionId(userSession.getSessionId());

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("afterConnectionClosed ==> id: {}, code: {}",
                session.getId(), status.getCode());
    }

    public boolean send(Request req) throws IOException {

        if (session != null && session.isOpen()) {
            if (!req.getMethod().equals("OPTIONS")) { // disable log OPTIONS
                logger.info("SEND MESSAGE TO FREESWITH: {} ({})\n\n{}", fsWebSocketAddress, session.getId(), req);
            }
            TextMessage msg = new TextMessage(req.toString());
            session.sendMessage(msg);
            return true;
        } else {
            String callID = req.getHeader("Call-ID").toString().replace("Call-ID: ", "").trim();
            logger.error("CALLOUT|" + callID + "|CANNOT SENT TO: {} A MESSAGE\n{} ", fsWebSocketAddress, req.toString());
            return false;
        }
    }

    public boolean sendResponse(Response response) throws IOException {

        if (session != null && session.isOpen()) {
            logger.info("SEND MESSAGE TO FREESWITH: {} ({})\n\n{}", fsWebSocketAddress, session.getId(), response);
            TextMessage msg = new TextMessage(response.toString());
            session.sendMessage(msg);
            return true;
        } else {
            logger.error("CANNOT SENT TO: {} A MESSAGE\n{} ", fsWebSocketAddress, response.toString());
            return false;
        }
    }

    public boolean reconnect() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        try {
            ListenableFuture<WebSocketSession> sessionListenableFuture = webSocketClient.doHandshake(this, new WebSocketHttpHeaders(), new URI(this.fsWebSocketAddress));
            session = sessionListenableFuture.get();
            logger.info("FINISH INIT WEB-SOCKET RE-CONNECTION: {} - {}", session.getId(), session.isOpen());
        } catch (InterruptedException | ExecutionException | URISyntaxException e) {
            logger.info("CANNOT INIT WEB-SOCKET RE-CONNECTION: {}", fsWebSocketAddress);
            return false;
        }
        return true;
    }

    public boolean isConnected() {
        Request request = SipMessageBuilder.buildOptionRequest("1000", "sip:1000@" + freeswitchIp + "?X-sId=dy7p2gz1r4cj7j7a08k3ixn6hhny.739798", "ws", freeswitchIp);
        try {
            return send(request);
        } catch (IOException e) {
            return false;
        }
    }
}
