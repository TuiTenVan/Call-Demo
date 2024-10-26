package com.freeswitch.demoCall.service.processor;

import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.utils.SipUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import io.sipstack.netty.codec.sip.SipMessageEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class CallInMgr {
    private static final int CACHED_EXPIRED = 2;// hours
    private static final Object lock = new Object();
    private static CallInMgr instance;
    private final Cache<String, UA> uaCalls;

    public CallInMgr() {
        uaCalls = CacheBuilder.newBuilder().expireAfterWrite(CACHED_EXPIRED, TimeUnit.HOURS)
                .removalListener(new com.google.common.cache.RemovalListener<String, UA>() {

                    @Override
                    public void onRemoval(RemovalNotification<String, UA> removalListener) {
                        log.debug("[RemovalNotification UA], Key: " + removalListener.getKey() + ", Value: "
                                + removalListener.getValue() + ", cause: " + removalListener.getCause().name());
                    }
                }).build();

    }

    public static CallInMgr instance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CallInMgr();
                }
            }
        }
        return instance;
    }

    public UA get(String callID) {
        return uaCalls.asMap().get(callID);
    }

    public UA put(UA ua) {
        final SipMessageEvent sipEvent = ua.getSipEvent();
        String callID = SipUtil.getCallID(sipEvent.getMessage());
        return uaCalls.asMap().put(callID, ua);
    }

    public UA remove(UA ua) {
        final SipMessageEvent sipEvent = ua.getSipEvent();
        String callID = SipUtil.getCallID(sipEvent.getMessage());
        return uaCalls.asMap().remove(callID);
    }

    public UA remove(String callID) {
        return uaCalls.asMap().remove(callID);
    }

    public boolean containsKey(String callID) {
        return uaCalls.asMap().containsKey(callID);
    }

    public int size() {
        return uaCalls.asMap().size();
    }

}
