package com.freeswitch.demoCall.service.callout;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Log4j2
public class SipWebsocketManager {
    private static final Map<Integer, SipWebsocketService> runningFreeswitchs = new ConcurrentHashMap<>();
    private static final Map<Integer, SipWebsocketService> pendingFreeswitchs = new ConcurrentHashMap<>();
    private static final AtomicLong INDEX = new AtomicLong();
//    private static final List<String> listSessionId = new ArrayList<>();

    @Value("${ringme.callout.sip.websockets}")
    private String[] listOfFreeswitch;

    @Value("${ringme.callout.sip.gw.address}")
    private String[] listGwAddress;

    @Autowired
    private ApplicationContext context;

    private Timer timer;

    @PostConstruct
    public void initialize() {
        try {
            log.info("initialize SipWebsocketManager");
            AtomicInteger i = new AtomicInteger();
            Arrays.stream(listOfFreeswitch).forEach(wsAddress -> {
                SipWebsocketService fsConnection = new SipWebsocketService(wsAddress, context);
                int number = i.getAndIncrement();
                runningFreeswitchs.put(number, fsConnection);
            });
//            timer = new Timer("Freeswitch-Ping-Scheduler");
//            timer.scheduleAtFixedRate(this, 60000, 60000);
        } catch (Exception e) {
            log.error("[SipWebsocketService] Initialization error", e);
        }
    }

    public Map<String, Object> getSipWebsocketService() {
        if (runningFreeswitchs.isEmpty()) {
            log.info("[SipWebsocketService] Empty map");
            return null;
        }

        int index = (int) Math.abs(INDEX.incrementAndGet() % runningFreeswitchs.size());

        if (index > 65000) {
            INDEX.set(0);
        }

        if (runningFreeswitchs.size() > 0) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", index);
            map.put("ws", runningFreeswitchs.get(index));
            map.put("address", listGwAddress[index]);
            return map;
        }

        return null;

    }

    public SipWebsocketService getSipWebsocketServiceThreadById(String id) {
        if (runningFreeswitchs.isEmpty()) {
            log.info("[SipWebsocketService] Empty map");
            return null;
        }

        return runningFreeswitchs.get(Integer.parseInt(id));
    }

    @Scheduled(fixedRate = 60000)
    public void run() {
//        log.info("Checking pending({}), running({}) ...", pendingFreeswitchs.size(), runningFreeswitchs.size());
        pendingFreeswitchs.keySet().forEach(num -> {
            SipWebsocketService fsConnection = pendingFreeswitchs.get(num);
            if (fsConnection.reconnect()) {
                pendingFreeswitchs.remove(num);
                runningFreeswitchs.put(num, fsConnection);
            }
        });

        runningFreeswitchs.keySet().forEach(num -> {
            SipWebsocketService fsConnection = runningFreeswitchs.get(num);
            if (!fsConnection.isConnected()) {
                runningFreeswitchs.remove(num);
                pendingFreeswitchs.put(num, fsConnection);
            }
        });

        log.info("Finish checking pending({}), running({})", pendingFreeswitchs.size(), runningFreeswitchs.size());
    }
}
