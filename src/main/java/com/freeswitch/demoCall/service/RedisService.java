package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.common.CDRKey;
import com.freeswitch.demoCall.sip.UserSession;
import com.google.gson.Gson;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final Logger logger = LogManager.getLogger(RedisService.class);

    @Autowired
    @Qualifier(value = "redisTemplate2")
    private RedisTemplate<String, Object> jedisTemplate2;



//    @Value("${app.instance-id}")
//    private String appInstanceId;

    @Value("${ringme.env}")
    private String env;


    @Autowired
    private Gson gson;

    public static final String RECENT_CALLIN = ":recent_callin"; // redis hashmap ( key, caller, callID)

    public static final String RECENT_CALLOUT = ":recent_callout"; // redis hashmap (key, callee, data)

    public static final String CALLOUT_CALLING = ":callout_calling"; // redis hashmap (key, callee, data)

    public String getPrefixKeyWithInstanceId() {
        return env + CDRKey.CALLOUT;
    }

    private String getListCalloutKeyRedis() {
        return env + CDRKey.LIST_CALLOUT_KEY ;
    }

    /* put thông tin Invite Callout lên redis
     * trường hợp restart server có thể remake lại UserSession */
    public void putCDRInvitetoRedis(UserSession userSession,
                                    long timeInvite,
                                    SIPRequest sipRequest) {

        Map<String, Object> map = new HashMap<>();
        map.put(CDRKey.CALLER, userSession.getCaller());
        map.put(CDRKey.CALLEE, userSession.getCallee());
        map.put(CDRKey.OTT_USERNAME, userSession.getFrom());
        map.put(CDRKey.TIME_INVITE, String.valueOf(timeInvite));
        map.put(CDRKey.SIP_REQUEST, sipRequest);
        map.put(CDRKey.SIP_WEBSOCKET_ID, userSession.getSipWebsocketId());
        map.put(CDRKey.TIME_REMAIN, userSession.getTimeRemain());

        logger.info("CALLOUT|" + userSession.getCaller() + "|" + userSession.getCallee() +
                "|" + userSession.getSessionId() + "PUSH CDR INVITE CALLOUT TO REDIS");
        String rKey = getPrefixKeyWithInstanceId() + userSession.getSessionId();
        jedisTemplate2.opsForHash().putAll(rKey, map);
        jedisTemplate2.opsForSet().add(getListCalloutKeyRedis(), rKey);
    }

    public Set<Object> getListCalloutKeys() {
        logger.info("getListCalloutKeys: {}", getListCalloutKeyRedis());
        return jedisTemplate2.opsForSet().members(getListCalloutKeyRedis());
    }

    public Map<Object, Object> getRecentCallout(String key) {

        return jedisTemplate2.opsForHash().entries(key);
    }

    public void setTimeoutKeyRecentCallout(String key) {
        jedisTemplate2.expire(key, 60, TimeUnit.SECONDS);
    }
    public void removeKeyInListCallout(String key) {
        jedisTemplate2.opsForSet().remove(getListCalloutKeyRedis(), key);
    }
    /* xóa expired time cache redis key */
    public void rePushCallout(String sessionId) {
        String rKey = getPrefixKeyWithInstanceId() + sessionId;
        jedisTemplate2.opsForHash().putAll(rKey, jedisTemplate2.opsForHash().entries(rKey));
    }

    /* cần cache vì lúc process code callout cần SIPResponse */
    public void updateResponseSipToRedis(String sessionId, SIPResponse sipResponse) {
        jedisTemplate2.opsForHash().put(getPrefixKeyWithInstanceId() + sessionId, CDRKey.SIP_RESPONSE, sipResponse);
    }

    /* cần cache vì lúc restart server nếu nhận được code 101 type = 1 sẽ cần start OCS */
    public void updateCDRAcceptToRedis(String sessionId, long timeStart) {
        jedisTemplate2.opsForHash().put(getPrefixKeyWithInstanceId() + sessionId, CDRKey.TIME_START, String.valueOf(timeStart));
    }

    public String getTimeAcceptStrFromRedis(String sessionId) {
        Object obj = jedisTemplate2.opsForHash().get(getPrefixKeyWithInstanceId() + sessionId, CDRKey.TIME_START);
        if (Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public void clearRedis(String sessionId) {
        String rKey = getPrefixKeyWithInstanceId() + sessionId;
        jedisTemplate2.delete(rKey);
        jedisTemplate2.opsForSet().remove(getListCalloutKeyRedis(), rKey);
    }
    /* ======================================================================================== */
    public void putRecentCallout(UserSession userSession, String orderId, String additionaldata) {

        // key = callee ( viễn thông )
        Map<String, Object> map = new HashMap<>();
        map.put("caller:" + userSession.getCallee(), userSession.getCaller());
        map.put("jid:" + userSession.getCallee(), userSession.getFrom().split("\\/")[0]); // bỏ /resource
        map.put("calleename:" + userSession.getCallee(), userSession.getCalleeName());

        if(orderId != null) {
            map.put("order:" + userSession.getCallee(), orderId);
        } else {
            jedisTemplate2.opsForHash().delete(env + RECENT_CALLOUT, "order:" + userSession.getCallee());
        }
        if(additionaldata != null) {
            map.put("additionaldata:" + userSession.getCallee(), additionaldata);
        } else {
            jedisTemplate2.opsForHash().delete(env + RECENT_CALLOUT, "additionaldata:" + userSession.getCallee());
        }

        jedisTemplate2.opsForHash().putAll(env + RECENT_CALLOUT, map);
    }

    public Map<String, String> getRecentCalleeForCallin(String caller, boolean getAll) {

        Object recentCallee = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "caller:" + caller);
        Object recentJid = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "jid:" + caller);
        if (Objects.nonNull(recentCallee) && Objects.nonNull(recentJid)) {

            Map<String, String> map = new HashMap<>();
            map.put("callee", String.valueOf(recentCallee));
            map.put("jid", String.valueOf(recentJid));

            if(getAll) {
                Object recentCalleeName = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "calleename:" + caller);
                Object recentOrder = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "order:" + caller);
                Object recentAdditionaldata = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "additionaldata:" + caller);

                if (Objects.nonNull(recentCalleeName)) {
                    map.put("calleeName", String.valueOf(recentCalleeName));
                }
                if (Objects.nonNull(recentOrder)) {
                    map.put("order", String.valueOf(recentOrder));
                }
                if (Objects.nonNull(recentAdditionaldata)) {
                    map.put("additionaldata", String.valueOf(recentAdditionaldata));
                }
            }
            return map;
        } else {
//            Map<String, Object> recentCdr = callCDRDao.getRecentCdrByCallee(caller);
//            if (!recentCdr.isEmpty()) {
//
//                Map<String, Object> mapToRedis = new HashMap<>();
//                logger.info(recentCdr);
//                mapToRedis.put("caller:" + caller, recentCdr.get("caller"));
//                mapToRedis.put("jid:" + caller, recentCdr.get("ott_username")); // TODO(HIGH): add calleeName
//                if (recentCdr.get("order_id") != null) {
//                    mapToRedis.put("order:" + caller, recentCdr.get("order_id"));
//                }
//
//                if (recentCdr.get("additionaldata") != null) {
//                    mapToRedis.put("additionaldata:" + caller, recentCdr.get("additionaldata"));
//                }
//                jedisTemplate2.opsForHash().putAll(env + RECENT_CALLOUT, mapToRedis);
//                Map<String, String> map = new HashMap<>();
//                map.put("callee", String.valueOf(recentCdr.get("caller")));
//                map.put("jid", String.valueOf(recentCdr.get("ott_username")));
//                return map;
//            }
        }
        return null;
    }

    public String getAdditionalDataCallout (String callee) {
        Object recentAdditionaldata = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "additionaldata:" + callee);

        if (Objects.nonNull(recentAdditionaldata)) {
            return String.valueOf(recentAdditionaldata);
        }
        return null;

    }
    public String getCalleeNameRecentCallout(String callee) {
        Object calleeNameObj = jedisTemplate2.opsForHash().get(env + RECENT_CALLOUT, "calleename:" + callee);
        if(Objects.nonNull(calleeNameObj)) {
            return String.valueOf(calleeNameObj);
        }
        return null;
    }
    /* ======================================================================================== */

    /* cần cache để kiểm tra xem số điện thoại có đang trong cuộc gọi hay không ( không cho phép 1 sdt được gọi callout 2 cuộc cùng lúc */
    public void pushCallingForCaller(String caller) { // check calling
        jedisTemplate2.opsForHash().put(env + CALLOUT_CALLING, "status:" + caller, "calling");
    }
    public boolean checkCallingForCaller(String caller) {
        Object checkCallingForCaller = jedisTemplate2.opsForHash().get(env + CALLOUT_CALLING, "status:" + caller);
        // TODO(LOW): check mysql ?
        return Objects.nonNull(checkCallingForCaller) && String.valueOf(checkCallingForCaller).equals("calling");
    }

    public void removeCallingForCaller(String caller) {
        logger.info("CALLOUT|{}|removeCallingForCaller", caller);
        jedisTemplate2.opsForHash().delete(env + CALLOUT_CALLING, "status:" + caller);
    }


    public void removeAllCallingCallout() {
        Map<Object, Object> map = jedisTemplate2.opsForHash().entries(env + CALLOUT_CALLING);
        map.forEach((key, value) -> {
            if(String.valueOf(key).contains("status:") && String.valueOf(value).equals("calling")) {

                logger.info("=============> key {} === value {}", key, value);
                removeCallingForCaller(String.valueOf(key).replace("status:", ""));
            }
        });
    }
    /* ======================================================================================== */

    public void putRecentCallinStatusCode(String sessionId, int statusCode) {
        logger.info("putRecentCallinStatusCode|" + sessionId + "|" + statusCode);
        jedisTemplate2.opsForHash().put(env + RECENT_CALLIN, "code:" + sessionId, statusCode);
    }
    public Integer getRecentCallinCode(String sessionId) {
        Object obj = jedisTemplate2.opsForHash().get(env + RECENT_CALLIN, "code:" + sessionId);
        if(Objects.nonNull(obj)) {
            return Integer.valueOf(String.valueOf(obj));
        }
        return null;
    }

    /* cần cache vì có trường hợp ESLInbound không trả về đúng callId => cần lấy callId được cache theo số điện thoại người gọi */
    public void putRecentCallinCallId(String caller, String callId) {
        if(callId != null) {

            logger.info("CALLIN|" + caller + "|" + callId + "|putRecentCallinCallId");
            Map<String, Object> map = new HashMap<>();
            map.put(caller, callId);
            jedisTemplate2.opsForHash().putAll(env + RECENT_CALLIN, map);
        } else {
            logger.info("CALLIN|" + caller + "|" + null + "|callId is null");
        }
    }

    public void removeRecentCallinCallId(String caller) {
        String callId = getRecentCallinCallId(caller);
        if (callId != null) {

            jedisTemplate2.opsForHash().delete(env + RECENT_CALLIN, caller, callId, "code:" + callId);
            logger.info("CALLIN|" + caller + "|" + callId + "|removeRecentCallinCallId");
        } else {
            logger.error("CALLIN|" + caller + "|removeRecentCallinCallId|FAIL");
        }
    }

    public String getRecentCallinCallId(String caller) { // event answer, event hangup
        Object obj = jedisTemplate2.opsForHash().get(env + RECENT_CALLIN, caller);
        if(Objects.nonNull(obj)) {
            return String.valueOf(obj);
        }
        return null;
    }

    public String getRecentCallinGatewayId(String callId) { // event answer, event hangup
        Object obj = jedisTemplate2.opsForHash().get(env + RECENT_CALLIN, callId);
        if(Objects.nonNull(obj)) {
            logger.info("getRecentCallinGatewayId: {} -> {}", callId, String.valueOf(obj));
            return String.valueOf(obj);
        }
        return null;
    }
}
