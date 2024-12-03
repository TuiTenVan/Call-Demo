package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.config.Configuration;
import com.freeswitch.demoCall.mysql.dao.CallCDRDao;
import com.freeswitch.demoCall.mysql.entity.CallCDR;
import com.freeswitch.demoCall.mysql.entity.IVRHotline;
import com.freeswitch.demoCall.mysql.entity.IvrQueue;
import com.freeswitch.demoCall.mysql.entity.UserInfo;
import com.freeswitch.demoCall.queue.KafkaProducerService;
import com.freeswitch.demoCall.service.callin.queue.AgentCall;
import com.freeswitch.demoCall.service.callin.queue.CallToQueueServiceV2;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.sip.UserRegistry;
import com.freeswitch.demoCall.sip.UserSession;
import com.freeswitch.demoCall.utils.Utils;
import com.freeswitch.demoCall.utils.XmppStanzaUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.jivesoftware.smack.packet.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.net.URLDecoder;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CallService {

    private final Logger logger = LogManager.getLogger(CallService.class);

    @Value("${ringme.domain.xmpp}")
    private String domainXmpp;

//    @Value("${ringme.microservice.log.baseUrl}")
//    private String logServiceBaseUrl;
//
//    @Value("${ringme.microservice.api.baseUrl}")
//    private String apiServiceBaseUrl;

    @Autowired
    private UserRegistry registry;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private Gson gson;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private Configuration configuration;


    private static final String KEY_REDIS_OWNER_SESSION = "OWNER_SESSION_CALL";

    private static final String KEY_REDIS_AGENT_SESSION = "AGENT_SESSION_CALL";

    @Autowired
    @Qualifier(value = "redisTemplate2")
    private RedisTemplate<String, Object> jedisTemplate2;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    @Autowired
    private CallCDRDao callCDRDao;

    @Autowired
    private CallToQueueServiceV2 callToQueueServiceV2;

    public static final ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    public void checkAndSendByeRequestPending(Map<String, String> map) { // TODO(LOW): khác server do active standby

        if (registry.exists(map.get("call_id"))) { // nếu hangup/cancel mà chưa send byte request ( do restart server)

            UserSession userSession = registry.getBySessionId(map.get("call_id"));
            // restart and not handle request 203
            kafkaProducerService.sendMessage(XmppStanzaUtil.makeXmlBye(userSession, map.get("duration"), domainXmpp));
            registry.removeBySessionId(userSession.getSessionId());
        }
    }


    public void pushRedisNotify(String calleeUsername,
                                 String session,
                                 String status,
                                 Message message) {


        try {
            Map<String, Object> map = new HashMap<>();
            map.put("username", calleeUsername);
            map.put("session", session);
            map.put("timestamp", System.currentTimeMillis());
            String uri;
            if (!status.equals("486")) {

                uri = "/api/v1/call/invite";
                map.put("message", message.toXML().toString());
            } else {
                uri = "/api/v1/call/cancel";
            }

//            webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path(uri).build())
//                    .bodyValue(map)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public boolean notifyRecordToNami(String callId, EslEvent event, boolean isAnswer) {

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");

            // TODO: cache cdr ìnfo by callId
            CallCDR callCdr = callCDRDao.getBySessionId(callId);
            boolean enableSTT = false;
            if (callCdr.getCallType().equals("callivr")) {

                enableSTT = event.getEventHeaders().get("variable_ringme_stt") != null;
            }

            if (enableSTT) {
                logger.info(callId + "|" + callCdr.getCallType() + "ENABLE_SPEECH_TO_TEXT");
                URIBuilder b = new URIBuilder("http://30.29.0.175:8083/" + (isAnswer ? "start-record" : "stop-record"));
                b.addParameter("call_id", callId);
                b.addParameter("time", sdf.format(new Date()));
                b.addParameter("type_call", callCdr.getCallType());
                b.addParameter("hotline", callCdr.getHotline());
//                b.addParameter("from", callCdr.getCallerUsername() + "@ringme.vn");

                Map<String, Object> queueInfo = redisQueueSessionService.getIvrQueueByCallId(callId);
                if (!queueInfo.isEmpty()) {
                    b.addParameter("queueId", String.valueOf(queueInfo.get("queueId")));
                    b.addParameter("queueName", String.valueOf(queueInfo.get("queueName")));
                }
                if (callCdr.getCallType().equals("callout")) {
                    b.addParameter("from", callCdr.getCallerUsername() + "@ringme.vn");
                    b.addParameter("to", Utils.validatePhone(callCdr.getCalleePhoneNumber(), "VN") + "@ringme.vn");
                } else {

                    b.addParameter("from", Utils.validatePhone(callCdr.getCallerPhoneNumber(), "VN") + "@ringme.vn");
                    b.addParameter("to", callCdr.getCalleeUsername() + "@ringme.vn");
                }

                URL url = b.build().toURL();
                String decodedUrl = URLDecoder.decode(url.toString(), "UTF-8");
                RestTemplate restTemplate = new RestTemplate();

                ResponseEntity<String> responseEntity = restTemplate.exchange(
                        decodedUrl,
                        HttpMethod.GET,
                        null,
                        String.class);

                if (responseEntity.getStatusCodeValue() == HttpStatus.OK.value()) {
                    logger.info(callId + "|notifyRecordToNami|POST|{}|{}", decodedUrl, responseEntity.getBody());
                    if (callCDRDao.setSpeechToText(callId) == 1) {
                        logger.info(callId + "|setSpeechToText|SUCCESS");
                    }
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(),ex);
        }
        return false;
    }

    public void sendToKafkaCCU(String type, EslEvent eslEvent, boolean increment) {
        try {
            if (eslEvent.getEventHeaders().get("variable_ringme_call_ivr") != null) {

                if (eslEvent.getEventHeaders().get("variable_ringme_call_partner") != null) {

                    String slug = eslEvent.getEventHeaders().get("variable_ringme_call_partner");
                    logger.info("check CCU call_ivr|{}|{}=>{}|{}", type, slug, "callin", "ringme_callin");
                } else {

                    logger.info("CALL IVR => not handle check CCU");
                }
            } else if (type.equals("callout")) {
                if (eslEvent.getEventHeaders().get("Caller-Destination-Number").contains("_gw_")) {

                    String slug = eslEvent.getEventHeaders().get("Caller-Destination-Number").split("_gw_")[1];
                } else {
                    logger.info("gateway slug not valid");
                }
            } else {
                String slug = eslEvent.getEventHeaders().get("variable_ringme_call_partner");

                if (slug == null) {
                    slug = eslEvent.getEventHeaders().get("variable_sip_gateway_name");
                }
                 else {
                    logger.info("gateway slug not valid");
//                    printLog(eslEvent);
                }
            }

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
    public void sendCallInviteToKafka(CallCDR callCDR, UserInfo client, UserInfo agent) {

        Map<String, Object> mapNotify = new HashMap<>();
        mapNotify.put("type", "call_invite");
        mapNotify.put("ownerId", callCDR.getOwnerId());
        jedisTemplate2.opsForHash().put(KEY_REDIS_OWNER_SESSION, callCDR.getSessionId(), callCDR.getOwnerId());
        Map<String, String> data = new HashMap<>();
        data.put("call_type", callCDR.getCallType());
        data.put("session_id", callCDR.getSessionId());

        if (callCDR.getCallType().equals("callout")) {

            data.put("agent_phonenumber", callCDR.getCallerPhoneNumber());
            data.put("agent_username", callCDR.getCallerUsername());

            if (agent != null) {

                data.put("agent_avatar", agent.getAvatar());
                data.put("agent_fullname", agent.getFullname());
            }

            data.put("client_phonenumber", callCDR.getCalleePhoneNumber());
            data.put("client_username", callCDR.getCalleeUsername());
        } else {

            data.put("client_phonenumber", callCDR.getCallerPhoneNumber());
            data.put("client_username", callCDR.getCallerUsername());
            if (client != null) {
                data.put("client_avatar", client.getAvatar());
                data.put("client_fullname", client.getFullname());
            }

            data.put("agent_phonenumber", callCDR.getCalleePhoneNumber());
            data.put("agent_username", callCDR.getCalleeUsername());
            if (agent != null) {

                data.put("agent_avatar", agent.getAvatar());
                data.put("agent_fullname", agent.getFullname());
            }
        }

        data.put("hotline", callCDR.getHotline());
        data.put("order_id", callCDR.getOrderId());
        data.put("time", formatter.get().format(new Date()));
        mapNotify.put("data", data);

        String dataSend = gson.toJson(mapNotify);
        kafkaTemplate.send(configuration.getNotiTopic(), dataSend);
        logger.info("push2Queue|Message|" + configuration.getNotiTopic() +"|" + dataSend);

    }

    public void sendCallAnswerToKafka(String sessionId, boolean isIvr) {

        Map<String, Object> mapNotify = new HashMap<>();
        mapNotify.put("type", "call_answer");
        mapNotify.put("ownerId", getOwnerId(sessionId, false));

        Map<String, Object> data = new HashMap<>();
        data.put("session_id", sessionId);

        if (isIvr) {
            UserInfo agent = getAgentInfoByCallId(sessionId);

            if (agent !=  null) {

                data.put("agent_phonenumber", agent.getPhoneNumber());
                data.put("agent_username", agent.getUsername());
                data.put("agent_avatar", agent.getAvatar());
                data.put("agent_fullname", agent.getFullname());
            }

            Map<String, Object> queueInfo = redisQueueSessionService.getIvrQueueByCallId(sessionId);
            data.putAll(queueInfo);
        }

        data.put("time", formatter.get().format(new Date()));
        mapNotify.put("data", data);

        String dataSend = gson.toJson(mapNotify);
        kafkaTemplate.send(configuration.getNotiTopic(), dataSend);
        logger.info("push2Queue|Message|" + configuration.getNotiTopic() +"|" + dataSend);
    }

    public void sendCallCloseToKafka(String sessionId, boolean isCancelIvr) {

        if (isCancelIvr) {
            removeAgentInfoByCallId(sessionId);
        }
        Map<String, Object> mapNotify = new HashMap<>();
        mapNotify.put("type", "call_close");
        mapNotify.put("ownerId", getOwnerId(sessionId, true));

        Map<String, Object> data = redisQueueSessionService.getIvrQueueByCallId(sessionId);

        data.put("session_id", sessionId);
        data.put("time", formatter.get().format(new Date()));
        mapNotify.put("data", data);

        String dataSend = gson.toJson(mapNotify);
        kafkaTemplate.send(configuration.getNotiTopic(), dataSend);
        logger.info("push2Queue|Message|" + configuration.getNotiTopic() +"|" + dataSend);
    }

    private Integer getOwnerId(String sessionId, boolean isRemove) {

        Object obj = jedisTemplate2.opsForHash().get(KEY_REDIS_OWNER_SESSION, sessionId);
        if (Objects.nonNull(obj)) {
            if (isRemove) {
                jedisTemplate2.opsForHash().delete(KEY_REDIS_OWNER_SESSION, sessionId);
            }
            return Integer.valueOf(String.valueOf(obj));
        }
        return null;
    }

    /* -------------------------------------- cache agent info caller for call ivr -----------------------------------------------------*/
    public void cacheAgentInfoForCallId(String originCallId, UserInfo userInfo) {
        try {

            jedisTemplate2.opsForHash().put(KEY_REDIS_AGENT_SESSION, originCallId, gson.toJson(userInfo));
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public UserInfo getAgentInfoByCallId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(KEY_REDIS_AGENT_SESSION, callId);
        if (Objects.nonNull(obj)) {
            removeAgentInfoByCallId(callId);
            return gson.fromJson(String.valueOf(obj), UserInfo.class);
        }
        return null;
    }

    public void removeAgentInfoByCallId(String callId) {

        logger.info("removeAgentInfoByCallId: {}", callId);
        jedisTemplate2.opsForHash().delete(KEY_REDIS_AGENT_SESSION, callId);
    }

    /* -------------------------------------- send to kafka queue agents -----------------------------------------------------*/

//    public List<Object> getListQueueAgent(List<Integer> listQueueId, Integer ownerId) {
//
//        List<IvrQueue> listQueue = listQueueId.stream()
//                .map(item -> callToQueueServiceV2.getIvrQueue(String.valueOf(item)))
//                .collect(Collectors.toList());
//        List<Object> list = listQueue.stream()
//                .map(item -> getQueueAgentsDetail(item, ownerId))
//                .collect(Collectors.toList());
//        return list;
//    }

    public Map<String, Object> getQueueAgentsDetail(IvrQueue ivrQueue, Integer ownerId) {
        List<AgentCall> list = redisQueueService.getListAgentForQueue(ivrQueue.getId());
//        logger.info("getQueueAgentsDetail|{}", list);

        Map<String, Object> queueDetail = new HashMap<>();
        queueDetail.put("id", ivrQueue.getId());
        queueDetail.put("queueName", ivrQueue.getName());
        queueDetail.put("ownerId", ivrQueue.getOwnerId());

        queueDetail.put("totalAgent", list.size());

        List<AgentCall> listOnline = list.stream().filter(item -> {
            final String uri = "http://192.168.22.175:7700/api/user_sessions_info?user=" +
                    item.getUsername().replace("@" + domainXmpp, "") + "&host=" + domainXmpp;
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<List<Map<String, String>>> responseEntity = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, String>>>() {
                    });

            if (responseEntity.getStatusCodeValue() == HttpStatus.OK.value()) {

                List<Map<String, String>> pojoObjList = responseEntity.getBody();
                if (pojoObjList != null && !pojoObjList.isEmpty()) {
//                    logger.info("checkOnline|GET|{}|{}", uri, pojoObjList);
                    return pojoObjList.get(0).get("status").equals("available");
                }
            }
            return false;
        }).collect(Collectors.toList());
        queueDetail.put("agentOnlineDetail", listOnline);

        List<AgentCall> listAgentReady = listOnline.stream().filter(item -> {
            boolean validWrapupTime = redisQueueService
                    .checkCacheWrapupAgent(item.getUsername().replace("@" + domainXmpp, ""));

            boolean validNotCalling = !redisQueueService.getCacheAgentCalling(item.getPhoneNumber());

            return validWrapupTime && validNotCalling;
        }).collect(Collectors.toList());
        queueDetail.put("agentReadyDetail", listAgentReady);

        queueDetail.put("agentOnline", listOnline.size());
        queueDetail.put("agentReady", listAgentReady.size());

        queueDetail.put("agentRinging", redisQueueSessionService.countNumCallByStateInQueue(ivrQueue.getId(), 2));
        queueDetail.put("agentAnswer", redisQueueSessionService.countNumCallByStateInQueue(ivrQueue.getId(), 3));

        Map<String, Object> mapClient = redisQueueService.getDetailQueueClientWaiting(ivrQueue.getId());
        Set<Object> setCallId = (Set<Object>) mapClient.get("listClientWaiting");
        Set<CallCDR> setCallCdr = setCallId.stream()
                .map(item -> redisQueueSessionService.getCacheCallCdrBySessionId(String.valueOf(item))).collect(Collectors.toSet());

        queueDetail.put("clientWaiting", mapClient.get("clientWaiting"));
        queueDetail.put("listClientWaiting", setCallCdr);
        return queueDetail;
//        Map<String, Object> mapNotify = new HashMap<>();
//        mapNotify.put("type", "queue_agent_detail");
//        mapNotify.put("ownerId", ownerId);
//        mapNotify.put("data", queueDetail);
//
//        String dataSend = gson.toJson(mapNotify);
//        kafkaTemplate.send(configuration.getNotiTopic(), dataSend);
//        logger.info("push2Queue|Message|" + configuration.getNotiTopic() +"|" + dataSend);
    }

    public void sendCampaignCallState(String sessionId, String callee, int state, int callStatusCode) {
        try {
            // TODO: check call in campaign
            if (callee == null) {
                throw new InvalidParameterException("callee is null");
            }
            Map<String, Object> map = new HashMap<>();
            map.put("sessionCall", sessionId);
            map.put("state", state);
            map.put("callStatusCode", callStatusCode);

            String additionaldata = redisService.getAdditionalDataCallout(callee);
            if (additionaldata == null) {
                throw new InvalidParameterException("additionaldata not valid");
            }
            JsonObject jsonObject = gson.fromJson(additionaldata, JsonObject.class).getAsJsonObject("order_info");
            map.put("campaignCode", jsonObject.get("campaignCode").getAsString());
            map.put("campaignScriptCode", jsonObject.get("campaignScriptCode").getAsString());
            map.put("contactId", jsonObject.get("contactId").getAsString());
            map.put("customerPhone", jsonObject.get("customerPhone").getAsString());
            map.put("agentNameAssign", jsonObject.get("agentNameAssign").getAsString());
            map.put("ownerId", jsonObject.get("ownerId").getAsString());

            logger.info("sendCampaignCallState: {}|{}|{}", sessionId, callee, map);
//            Map response = webClientBuilder.baseUrl(apiServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri("/s2s/campaign/call-state")
//                    .header("Accept", "application/json, text/plain, */*")
//                    .contentType(MediaType.APPLICATION_JSON)
//                    .body(Mono.just(map), Map.class)
//                    .retrieve() // Lấy phản hồi từ API
//                    .bodyToMono(Map.class)
//                    .block(); // Chờ để lấy kết quả đồng bộ
//            logger.info(response);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}
