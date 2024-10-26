package com.freeswitch.demoCall.service.outbound.queue;

import com.freeswitch.demoCall.common.CallRedisKey;
import com.freeswitch.demoCall.mysql.entity.IVRHotline;
import com.freeswitch.demoCall.mysql.entity.IVRMenu;
import com.freeswitch.demoCall.mysql.entity.IvrQueue;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {
    private final Logger logger = LogManager.getLogger(RedisQueueService.class);

    @Autowired
    @Qualifier(value = "redisTemplate2")
    private RedisTemplate<String, Object> jedisTemplate2;

    @Value("${ringme.env}")
    private String env;

    @Autowired
    private Gson gson;

    private static final long timeoutCacheQueue = 1;

    // ============================== key = hotline or id => value = ivr_hotline =======================
    public void cacheIvrHotLine(String hotline, IVRHotline ivrHotline) {
        jedisTemplate2.opsForValue().set(env + CallRedisKey.KEY_IVR_HOTLINE + hotline, gson.toJson(ivrHotline));
        jedisTemplate2.expire(env + CallRedisKey.KEY_IVR_HOTLINE + hotline, timeoutCacheQueue, TimeUnit.HOURS);
    }

    public IVRHotline getCacheIvrHotLine(String hotline) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisKey.KEY_IVR_HOTLINE + hotline);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvrHotLine|HIT|{}", hotline);
            return gson.fromJson(String.valueOf(obj), IVRHotline.class);
        }
        logger.info("getCacheIvrHotLine|MISS|{}", hotline);
        return null;
    }
    // ============================== key = ivr_menu_id => value = ivr_menu =========================
    public void cacheIvrMenu(String key, IVRMenu ivrMenu) { // key = hotline or id
        jedisTemplate2.opsForValue().set(env + CallRedisKey.KEY_IVR_MENU + key, gson.toJson(ivrMenu));
        jedisTemplate2.expire(env + CallRedisKey.KEY_IVR_MENU + key, timeoutCacheQueue, TimeUnit.HOURS);
    }

    public IVRMenu getCacheIvrMenu(String key) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisKey.KEY_IVR_MENU + key);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvrMenu|HIT|{}", key);
            return gson.fromJson(String.valueOf(obj), IVRMenu.class);
        }
        logger.info("getCacheIvrMenu|MISS|{}", key);
        return null;
    }

    // ============================== key = ivr_queue_id => value = ivr_queue =======================
    public void cacheIvrQueue(String key, IvrQueue ivrQueue) { // key = hotline or id
        jedisTemplate2.opsForValue().set(env + CallRedisKey.KEY_IVR_QUEUE + key, gson.toJson(ivrQueue));
        jedisTemplate2.expire(env + CallRedisKey.KEY_IVR_QUEUE + key, timeoutCacheQueue, TimeUnit.HOURS);
    }

    public IvrQueue getCacheIvQueue(String key) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisKey.KEY_IVR_QUEUE + key);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvQueue|HIT|{}", key);
            return gson.fromJson(String.valueOf(obj), IvrQueue.class);
        }
        logger.info("getCacheIvQueue|MISS|{}", key);
        return null;
    }

    // ============================= key = call_id => value = hotline =================================
    public void cacheIvrForCallId(String callId, IVRMenu ivrMenu) {
        jedisTemplate2.opsForHash().put(env + CallRedisKey.KEY_IVR_CALLID, callId, gson.toJson(ivrMenu));
    }

    public IVRMenu getCacheIvrCallId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisKey.KEY_IVR_CALLID,  callId);
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
        jedisTemplate2.opsForHash().delete(env + CallRedisKey.KEY_IVR_CALLID, callId);
    }

    // =============================================================================================
    public void cacheAgentAfterHangup(String agentJid, String wrapupTime, String clientPhoneNumber) {

        logger.info("cacheAgentAfterHangup: {}|{}|{}", agentJid, wrapupTime, clientPhoneNumber);
        agentJid = agentJid.split("@")[0];
        if (wrapupTime != null) {

            cacheWrapupAgent(agentJid, Long.parseLong(wrapupTime));
        }
        setNearestAgentForClient(clientPhoneNumber, agentJid);
        setTimeHangupAgent(agentJid);
    }

    public void cacheWrapupAgent(String agentJid, long wrapupTime) {
        jedisTemplate2.opsForValue().set(env + CallRedisKey.KEY_IVR_WRAPUP + agentJid, 1);
        jedisTemplate2.expire(env + CallRedisKey.KEY_IVR_WRAPUP + agentJid, wrapupTime, TimeUnit.SECONDS);
        logger.info("cacheWrapupAgent|{}|{}-SECONDS", agentJid, wrapupTime);
    }

    public boolean checkCacheWrapupAgent(String agentJid) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisKey.KEY_IVR_WRAPUP + agentJid);
        if(Objects.nonNull(obj)) {
//            logger.info("checkCacheWrapupAgent|HIT|{}", agentJid);
            return false;
        }
//        logger.info("checkCacheWrapupAgent|MISS|{}", agentJid);
        return true;
    }

    public void setTimeHangupAgent(String agentJid) {
        jedisTemplate2.opsForHash().put(env + "ivr:agent_hangup:", agentJid, System.currentTimeMillis());
        // TODO: setTimeHangupAgent => delete key at end day
    }

    public long getTimeHangupAgent(String agentJid) {
        agentJid = agentJid.split("@")[0];
        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:agent_hangup:", agentJid);
        if(Objects.nonNull(obj)) {
            return Long.parseLong(String.valueOf(obj));
        }
        return 0;
    }

    public void setNearestAgentForClient(String clientPhoneNumber, String agentUsername) {

        jedisTemplate2.opsForHash().put(env + "ivr:nearest:", clientPhoneNumber, agentUsername);
        // TODO: setNearestAgentForClient => remove after ? => chuyen sang key value
    }
    public String getNearestAgent(String clientPhoneNumber) {
        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:nearest:", clientPhoneNumber);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public String getStatusCallAgent(String username) {
        Object obj = jedisTemplate2.opsForHash().get(CallRedisKey.KEY_REDIS_AGENT_STATUS_CALL, username);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    // ===================================== key=queueId, value= list agent call =================================
    public void cacheListAgentForQueue(Integer queueId, List<AgentCall> list) {
        jedisTemplate2.opsForHash().put(CallRedisKey.KEY_REDIS_LIST_AGENT_QUEUE, "QUEUE:" + queueId, gson.toJson(list));
    }

    public List<AgentCall> getListAgentForQueue(Integer queueId) {
        Type listType = new TypeToken<ArrayList<AgentCall>>(){}.getType();
        Object obj = jedisTemplate2.opsForHash().get(CallRedisKey.KEY_REDIS_LIST_AGENT_QUEUE, "QUEUE:" + queueId);

        if (Objects.nonNull(obj)) {
            return gson.fromJson(String.valueOf(obj), listType);
        }
        return new ArrayList<>();
    }

    // ================================== cache agent jid by caller for call ivr ticket ============================

    public void setJidTicketCall(String caller, String agentJid) {

        logger.info("setJidTicketCall: {} -> {}", caller, agentJid);
        jedisTemplate2.opsForHash().put(env + "ivr:ticket", caller, agentJid);
    }

    public String getJidTicketCall(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:ticket", caller);
        if(Objects.nonNull(obj)) {
            jedisTemplate2.opsForHash().delete(env + "ivr:ticket", caller);
            return String.valueOf(obj);
        }
        return null;
    }
    /* -------------------------------------- cache call id by caller for call ivr -----------------------------------------------------*/
    public void setCallIdIvrByCaller(String caller, String callId) {

        logger.info("setCallIdIvrByCaller: {} -> {}", caller, callId);
        jedisTemplate2.opsForHash().put(env + "ivr:call_id", caller, callId);
    }

    public String getCallIdIvrByCaller(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:call_id", caller);
        if(Objects.nonNull(obj)) {
//            jedisTemplate2.opsForHash().delete(env + "ivr:call_id", caller);
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCallIdIvrByCaller(String caller) {
        logger.info("removeCallIdIvrByCaller|{}", caller);
        jedisTemplate2.opsForHash().delete(env + "ivr:call_id", caller);
    }

    /* ------------------------------ cache order by caller for call ivr --------------------------------*/
    public void setCallOrderIvrByCaller(String caller, String additionaldata) {

        logger.info("setCallOrderIvrByCaller: {} -> {}", caller, additionaldata);
        jedisTemplate2.opsForHash().put(env + "ivr:additionaldata", caller, additionaldata);
    }

    public String getCallOrderIvrByCaller(String caller) {

        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:additionaldata", caller);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public void removeCallOrderIvrByCaller(String caller) {

        logger.info("removeCallOrderIvrByCaller|{}", caller);
        jedisTemplate2.opsForHash().delete(env + "ivr:additionaldata", caller);
    }

    /* ----------------------------- cache queueInfoDetail by call id for call ivr --------------------------*/
    public void cacheIvrQueueByCallId(String callId, Map<String, Object> ivrQueueInfo) {

        logger.info("setIvrQueueByCallId: {} -> {}", callId, ivrQueueInfo);
        jedisTemplate2.opsForHash().put(env + "ivr:queueinfo", callId, gson.toJson(ivrQueueInfo));

        jedisTemplate2.opsForZSet().add(env + "ivr:queue_call:" + ivrQueueInfo.get("queueId"), callId, 1);
    }

    public Map<String, Object> getIvrQueueByCallId(String callId) {
        Object obj = jedisTemplate2.opsForHash().get(env + "ivr:queueinfo", callId);
        if(Objects.nonNull(obj)) {
//            removeIvrQueueByCallId(callId);

            Type mapType = new TypeToken<HashMap<String, Object>>(){}.getType();
            return gson.fromJson(String.valueOf(obj), mapType);
        }
        return new HashMap<>();
    }

    public void removeIvrQueueByCallId(String callId) {

        removeCallStateByCallId(callId);

        logger.info("removeIvrQueueByCallId|{}", callId);
        jedisTemplate2.opsForHash().delete(env + "ivr:queueinfo", callId);
    }

    /* ----------------------------- cache call state --------------------------*/
    public void cacheCallStateByCallId(String callId, Integer state) { // 1=waiting,2=ringing,3=answer

        logger.info("cacheCallStateByCallId: {} -> {}", callId, state);
        Map<String, Object> ivrQueueInfo = getIvrQueueByCallId(callId);
        if (!ivrQueueInfo.isEmpty()) {

            jedisTemplate2.opsForZSet().add(env + "ivr:queue_call:" + ivrQueueInfo.get("queueId"), callId, state);
        }
    }

    public Long countNumCallByStateInQueue(Integer queueId, Integer state) {

        Long count = jedisTemplate2.opsForZSet().count(env + "ivr:queue_call:" + queueId, state, state);
        return count;
    }

    public void removeCallStateByCallId(String callId) {

        logger.info("removeCallStateByCallId|{}", callId);
        Map<String, Object> ivrQueueInfo = getIvrQueueByCallId(callId);
        if (!ivrQueueInfo.isEmpty()) {

            jedisTemplate2.opsForZSet().remove(env + "ivr:queue_call:" + ivrQueueInfo.get("queueId"), callId);
        }
    }
}
