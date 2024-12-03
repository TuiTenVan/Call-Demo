package com.freeswitch.demoCall.service.callin.queue;

import com.freeswitch.demoCall.common.CallRedisIvrKey;
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

    // ============================== key = hotline or id => value = ivr_hotline =======================
    public void cacheIvrHotLine(String hotline, IVRHotline ivrHotline) {
        jedisTemplate2.opsForValue().set(env + CallRedisIvrKey.KEY_IVR_HOTLINE + hotline, gson.toJson(ivrHotline));
    }

    public IVRHotline getCacheIvrHotLine(String hotline) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisIvrKey.KEY_IVR_HOTLINE + hotline);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvrHotLine|HIT|{}", hotline);
            return gson.fromJson(String.valueOf(obj), IVRHotline.class);
        }
        logger.info("getCacheIvrHotLine|MISS|{}", hotline);
        return null;
    }
    // ============================== key = ivr_menu_id => value = ivr_menu =========================
    public void cacheIvrMenu(String key, IVRMenu ivrMenu) { // key = hotline or id
        jedisTemplate2.opsForValue().set(env + CallRedisIvrKey.KEY_IVR_MENU + key, gson.toJson(ivrMenu));
    }

    public IVRMenu getCacheIvrMenu(String key) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisIvrKey.KEY_IVR_MENU + key);
        if(Objects.nonNull(obj)) {
            logger.info("getCacheIvrMenu|HIT|{}", key);
            return gson.fromJson(String.valueOf(obj), IVRMenu.class);
        }
        logger.info("getCacheIvrMenu|MISS|{}", key);
        return null;
    }

    // ============================== key = ivr_queue_id => value = ivr_queue =======================
    public void cacheIvrQueue(String key, IvrQueue ivrQueue) { // key = hotline or id
        jedisTemplate2.opsForValue().set(env + CallRedisIvrKey.KEY_IVR_QUEUE + key, gson.toJson(ivrQueue));
    }

    public IvrQueue getCacheIvQueue(String key) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisIvrKey.KEY_IVR_QUEUE + key);
        if(Objects.nonNull(obj)) {
//            logger.info("getCacheIvQueue|HIT|{}", key);
            return gson.fromJson(String.valueOf(obj), IvrQueue.class);
        }
//        logger.info("getCacheIvQueue|MISS|{}", key);
        return null;
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
        jedisTemplate2.opsForValue().set(env + CallRedisIvrKey.KEY_IVR_WRAPUP + agentJid, 1);
        jedisTemplate2.expire(env + CallRedisIvrKey.KEY_IVR_WRAPUP + agentJid, wrapupTime, TimeUnit.SECONDS);
        logger.info("cacheWrapupAgent|{}|{}-SECONDS", agentJid, wrapupTime);
    }

    public boolean checkCacheWrapupAgent(String agentJid) {
        Object obj = jedisTemplate2.opsForValue().get(env + CallRedisIvrKey.KEY_IVR_WRAPUP + agentJid);
        if(Objects.nonNull(obj)) {
//            logger.info("checkCacheWrapupAgent|HIT|{}", agentJid);
            return false;
        }
//        logger.info("checkCacheWrapupAgent|MISS|{}", agentJid);
        return true;
    }

    public void setTimeHangupAgent(String agentJid) {
        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_TIME_AGENT_HANGUP, agentJid, System.currentTimeMillis());
        // TODO: setTimeHangupAgent => delete key at end day
    }

    public long getTimeHangupAgent(String agentJid) {
        agentJid = agentJid.split("@")[0];
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_TIME_AGENT_HANGUP, agentJid);
        if(Objects.nonNull(obj)) {
            return Long.parseLong(String.valueOf(obj));
        }
        return 0;
    }

    public void setNearestAgentForClient(String clientPhoneNumber, String agentUsername) {

        jedisTemplate2.opsForHash().put(env + CallRedisIvrKey.KEY_IVR_AGENT_NEAREST, clientPhoneNumber, agentUsername);
        // TODO: clear nearest after ?
    }
    public String getNearestAgent(String clientPhoneNumber) {
        Object obj = jedisTemplate2.opsForHash().get(env + CallRedisIvrKey.KEY_IVR_AGENT_NEAREST, clientPhoneNumber);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public String getStatusCallAgent(String username) {
        Object obj = jedisTemplate2.opsForHash().get(CallRedisIvrKey.KEY_REDIS_AGENT_STATUS_CALL, username);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    // ===================================== key=queueId, value= list agent call =================================
    public void cacheListAgentForQueue(Integer queueId, List<AgentCall> list) {
        jedisTemplate2.opsForHash().put(CallRedisIvrKey.KEY_REDIS_LIST_AGENT_QUEUE, "QUEUE:" + queueId, gson.toJson(list));
    }

    public List<AgentCall> getListAgentForQueue(Integer queueId) {
        Type listType = new TypeToken<ArrayList<AgentCall>>(){}.getType();
        Object obj = jedisTemplate2.opsForHash().get(CallRedisIvrKey.KEY_REDIS_LIST_AGENT_QUEUE, "QUEUE:" + queueId);

        if (Objects.nonNull(obj)) {
            return gson.fromJson(String.valueOf(obj), listType);
        }
        return new ArrayList<>();
    }

    /* ----------------------------- cache client waiting call to queue --------------------------*/

    public void cacheClientWaitingQueue(Integer queueId, String callId) {

        if (queueId != null && callId != null) {
            jedisTemplate2.opsForSet().add(env + CallRedisIvrKey.KEY_IVR_QUEUE_CLIENT_WAIT + queueId, callId);
            logger.info("cacheClientWaitingQueue|{}|{}", queueId, callId);
        }
    }

    public Long getNumClientWaitingQueue(Integer queueId) {
        return jedisTemplate2.opsForSet().size(env + CallRedisIvrKey.KEY_IVR_QUEUE_CLIENT_WAIT + queueId);
    }
    public Map<String, Object> getDetailQueueClientWaiting(Integer queueId) {
        Map<String, Object> map = new HashMap<>();
        map.put("clientWaiting", getNumClientWaitingQueue(queueId));
        map.put("listClientWaiting", jedisTemplate2.opsForSet().members(env + CallRedisIvrKey.KEY_IVR_QUEUE_CLIENT_WAIT + queueId));
        return map;
    }

    public boolean checkCallQueueExist(String callId, Integer queueId) {

        return Boolean.TRUE.equals(jedisTemplate2.opsForSet().isMember(env + CallRedisIvrKey.KEY_IVR_QUEUE_CLIENT_WAIT + queueId, callId));
    }

    /* ----------------------------- cache agent calling --------------------------*/
    public void cacheAgentCalling(String phoneNumber) {
        jedisTemplate2.opsForSet().add(env + CallRedisIvrKey.KEY_IVR_QUEUE_AGENT_CALLING, phoneNumber);
        logger.info("cacheAgentCalling|{}", phoneNumber);
    }

    public boolean getCacheAgentCalling(String phoneNumber) {

        return Boolean.TRUE.equals(jedisTemplate2.opsForSet().isMember(env + CallRedisIvrKey.KEY_IVR_QUEUE_AGENT_CALLING, phoneNumber));
    }

    public void removeCacheAgentCalling(String phoneNumber) {
        jedisTemplate2.opsForSet().remove(env + CallRedisIvrKey.KEY_IVR_QUEUE_AGENT_CALLING, phoneNumber);
        logger.info("removeCacheAgentCalling|{}", phoneNumber);
    }
}
