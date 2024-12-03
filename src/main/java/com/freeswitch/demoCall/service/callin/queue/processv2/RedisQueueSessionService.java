package com.freeswitch.demoCall.service.callin.queue.processv2;

import com.freeswitch.demoCall.common.CallRedisIvrKey;
import com.freeswitch.demoCall.mysql.entity.CallCDR;
import com.freeswitch.demoCall.mysql.entity.IVRMenu;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class RedisQueueSessionService {

    private final Logger logger = LogManager.getLogger(RedisQueueSessionService.class);

    @Autowired
    @Qualifier(value = "redisTemplate2")
    private RedisTemplate<String, Object> jedisTemplate2;

    @Value("${ringme.env}")
    private String env;

    @Autowired
    private Gson gson;

    /* -------------------------------------- cache call id by caller for call ivr -----------------------------------------------------*/
    public void setCallIdIvrByCaller(String caller, String callId) {

        logger.info("setCallIdIvrByCaller: {} -> {}", caller, callId);
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_CALL_ID_BY_CALLER, caller, callId);
    }

    public String getCallIdIvrByCaller(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_CALL_ID_BY_CALLER, caller);
        if(Objects.nonNull(obj)) {
//            jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_CALL_ID_BY_CALLER, caller);
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCallIdIvrByCaller(String caller) {
        logger.info("removeCallIdIvrByCaller|{}", caller);
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_CALL_ID_BY_CALLER, caller);
    }

    // ============================= key = call_id => value = hotline =================================
    public void cacheIvrForCallId(String callId, IVRMenu ivrMenu) {
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_CALLID, callId, gson.toJson(ivrMenu));
    }

    public IVRMenu getCacheIvrCallId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_CALLID,  callId);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvrCallId|HIT|{}", callId);
            return gson.fromJson(String.valueOf(obj), IVRMenu.class);
        }
        logger.info("getCacheIvrCallId|MISS|{}", callId);
        return null;
    }

    // clear after hangup or cancel ivr
    public void removeCacheIvrForCallId(String callId) {
        logger.info("removeCacheIvrForCallId|{}", callId);
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_CALLID, callId);
    }

    // ================================== cache agent jid by caller for call ivr ticket ============================

    public void setJidTicketCall(String caller, String agentJid) {

        logger.info("setJidTicketCall: {} -> {}", caller, agentJid);
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_TICKET, caller, agentJid);
    }

    public String getJidTicketCall(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_TICKET, caller);
        if(Objects.nonNull(obj)) {
            jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_TICKET, caller);
            return String.valueOf(obj);
        }
        return null;
    }

    /* ------------------------------ cache order by caller for call ivr --------------------------------*/
    public void setCallOrderIvrByCaller(String caller, String additionaldata) {

        logger.info("setCallOrderIvrByCaller: {} -> {}", caller, additionaldata);
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_ADDITIONAL_DATA, caller, additionaldata);
    }

    public String getCallOrderIvrByCaller(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_ADDITIONAL_DATA, caller);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCallOrderIvrByCaller(String caller) {

        logger.info("removeCallOrderIvrByCaller|{}", caller);
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_ADDITIONAL_DATA, caller);
    }

    /* ----------------------------- cache queueInfoDetail by call id for call ivr --------------------------*/
    public void cacheIvrQueueByCallId(String callId, Map<String, Object> ivrQueueInfo) {

        logger.info("setIvrQueueByCallId: {} -> {}", callId, ivrQueueInfo);
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_QUEUE_INFO, callId, gson.toJson(ivrQueueInfo));

        jedisTemplate2.opsForZSet().add(env + CallRedisIvrKey.KEY_IVR_QUEUE_STAT + ivrQueueInfo.get("queueId"), callId, 1);
    }

    public Map<String, Object> getIvrQueueByCallId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_QUEUE_INFO, callId);
        if(Objects.nonNull(obj)) {
//            removeIvrQueueByCallId(callId);

            Type mapType = new TypeToken<HashMap<String, Object>>(){}.getType();
            return gson.fromJson(String.valueOf(obj), mapType);
        }
        return new HashMap<>();
    }

    public void removeIvrQueueByCallId(String callId) {

        removeCallStateByCallId(callId);
        removeClientWaitingQueue(callId, null);
        removeCacheCallCdrBySessionId(callId);
        removeCacheVideoCallBySessionId(callId);
        removeCacheVideoRingBackBySessionId(callId);
        removeCacheIsIvrBySessionId(callId);

        logger.info("removeIvrQueueByCallId|{}", callId);
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_QUEUE_INFO, callId);
    }


    /* ----------------------------- cache call state --------------------------*/

    public Long countNumCallByStateInQueue(Integer queueId, Integer state) {

        Long count = jedisTemplate2.opsForZSet().count(env + CallRedisIvrKey.KEY_IVR_QUEUE_STAT + queueId, state, state);
        return count;
    }

    public void cacheCallStateByCallId(String callId, Integer state, String caller) { // 1=waiting,2=ringing,3=answer

        Map<String, Object> ivrQueueInfo = getIvrQueueByCallId(callId);
        if (!ivrQueueInfo.isEmpty()) {

            if (state.equals(2)) {
                Double score = jedisTemplate2.opsForZSet().score(env + CallRedisIvrKey.KEY_IVR_QUEUE_STAT + ivrQueueInfo.get("queueId"), callId);
                if (score != null && score == 3) {
                    return;
                }
            }
            jedisTemplate2.opsForZSet().add(env + CallRedisIvrKey.KEY_IVR_QUEUE_STAT + ivrQueueInfo.get("queueId"), callId, state);
            logger.info("cacheCallStateByCallId: {} -> {}", callId, state);

            if (state.equals(3)) {
                removeClientWaitingQueue(callId, String.valueOf(ivrQueueInfo.get("queueId")));
            }
        } else {
            logger.info("cacheCallStateByCallId: ivrQueueInfo null");
        }
    }

    public void removeCallStateByCallId(String callId) {

        logger.info("removeCallStateByCallId|{}", callId);
        Map<String, Object> ivrQueueInfo = getIvrQueueByCallId(callId);
        if (!ivrQueueInfo.isEmpty()) {

            jedisTemplate2.opsForZSet().remove(env + CallRedisIvrKey.KEY_IVR_QUEUE_STAT + ivrQueueInfo.get("queueId"), callId);
        }
    }

    public void removeClientWaitingQueue(String callId, String queueId) {
        if (queueId == null) {

            Map<String, Object> ivrQueueInfo = getIvrQueueByCallId(callId);
            if (!ivrQueueInfo.isEmpty()) {

                if (ivrQueueInfo.get("queueId") != null) {
                    queueId = String.valueOf(ivrQueueInfo.get("queueId"));
                }

            }
        }
        jedisTemplate2.opsForSet().remove(env + CallRedisIvrKey.KEY_IVR_QUEUE_CLIENT_WAIT + queueId, callId);
        logger.info("removeClientWaitingQueue|{}|{}", queueId, callId);
    }
    /* ----------------------------- cache callcdr by sessionId --------------------------*/

    public void cacheCallCdrBySessionId(String callId, CallCDR callCDR) {

        if (callId != null && callCDR != null) {
            jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_CALL_CDR, callId, gson.toJson(callCDR));
            logger.info("cacheCallCdrBySessionId|{}", callId);
        }
    }

    public CallCDR getCacheCallCdrBySessionId(String callId) {
        Object callCDRObj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_CALL_CDR, callId);
        if (Objects.nonNull(callCDRObj)) {
            return gson.fromJson(String.valueOf(callCDRObj), CallCDR.class);
        }
        return null;
    }

    public void removeCacheCallCdrBySessionId(String callId) {
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_CALL_CDR, callId);
    }

    /* ----------------------------- cache video call by sessionId --------------------------*/

    public void cacheVideoCallBySessionId(String callId) {

        if (callId != null) {
            jedisTemplate2.opsForSet().add(env + CallRedisIvrKey.KEY_IVR_IS_VIDEO_CALL, callId);
            logger.info("cacheVideoCallBySessionId|{}", callId);
        }
    }

    public boolean getCacheVideoCallBySessionId(String callId) {
        return Boolean.TRUE.equals(jedisTemplate2.opsForSet().isMember(env + CallRedisIvrKey.KEY_IVR_IS_VIDEO_CALL, callId));
    }

    public void removeCacheVideoCallBySessionId(String callId) {
        jedisTemplate2.opsForSet().remove(env + CallRedisIvrKey.KEY_IVR_IS_VIDEO_CALL, callId);
        logger.info("removeCacheVideoCallBySessionId|{}", callId);

    }

    /* ----------------------------- cache ringback link mp4 by sessionId --------------------------*/

    public void cacheVideoRingBackBySessionId(String callId, String link) {

        if (callId != null && link != null) {
            jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_LINK_RING_BACK_VIDEO, callId, link);
            logger.info("cacheVideoRingBackBySessionId|{}|{}", callId, link);
        }
    }

    public String getCacheVideoRingBackBySessionId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_LINK_RING_BACK_VIDEO, callId);
        if (Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCacheVideoRingBackBySessionId(String callId) {
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_LINK_RING_BACK_VIDEO, callId);
        logger.info("removeCacheVideoRingBackBySessionId|{}", callId);
    }

    /* ----------------------------- cache ivr param call by sessionId --------------------------*/
    public void cacheIsIvrBySessionId(String callId, int isIvr) {

        if (callId != null) {
            jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_IS_IVR_CALL, callId, isIvr);
            logger.info("cacheIsIvrBySessionId|{}|{}", callId, isIvr);
        }
    }

    public String getCacheIsIvrBySessionId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_IS_IVR_CALL, callId);
        if (Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCacheIsIvrBySessionId(String callId) {
        jedisTemplate2.opsForHash().delete(env + CallRedisIvrKey.KEY_IVR_IS_IVR_CALL, callId);
        logger.info("getCacheIsIvrBySessionId|{}", callId);
    }
}
