package com.freeswitch.demoCall.service.inbound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ESLInboundManager {
    private final Logger logger = LogManager.getLogger(ESLInboundManager.class);

    private static final Map<Integer, ESLInboundHandler> runningFreeswitchs = new ConcurrentHashMap<>();
    private static final Map<Integer, ESLInboundHandler> pendingFreeswitchs = new ConcurrentHashMap<>();

    @Value("${ringme.freeswitch.els.ips}")
    private String[] freeswitchElsIPs;

    @Autowired
    private ApplicationContext context;

    public void startAllESLInbound() {
        try {
            logger.info("start ESLInboundManager");
            AtomicInteger i = new AtomicInteger();
            Arrays.stream(freeswitchElsIPs).forEach(elsIp -> {
                ESLInboundHandler eslInboundHandler = new ESLInboundHandler(elsIp, context);
                int number = i.getAndIncrement();
                runningFreeswitchs.put(number, eslInboundHandler);
            });
        } catch (Exception e) {
            logger.error("[ESLInboundHandler] Initialization error", e);
        }
    }
    public void stopAllESLInbound() {
        try {

            logger.info("stop ESLInboundManager");
            runningFreeswitchs.keySet().forEach(num -> {
                ESLInboundHandler fsConnection = runningFreeswitchs.get(num);
                if (!fsConnection.isConnected()) {
                    fsConnection.disconnect();
                    runningFreeswitchs.remove(num);
                }
            });
            pendingFreeswitchs.keySet().forEach(pendingFreeswitchs::remove);
        } catch (Exception e) {
            logger.error("[ESLInboundHandler] Stop error", e);
        }
    }

    public void transferCall(String callId, String caller, String oldCallee, String newCallee, int type) {
        runningFreeswitchs.get(runningFreeswitchs.keySet().stream().findFirst().get())
                .transferCall(callId, caller, oldCallee, newCallee, type);
    }

    public void musicOnHold(String callId, String caller) {
        runningFreeswitchs.get(runningFreeswitchs.keySet().stream().findFirst().get())
                .musicOnHold(callId, caller);
    }

    @Scheduled(fixedRate = 60000)
    public void run() {
        pendingFreeswitchs.keySet().forEach(num -> {
            ESLInboundHandler fsConnection = pendingFreeswitchs.get(num);
            if (fsConnection.isConnected()) {
                pendingFreeswitchs.remove(num);
                runningFreeswitchs.put(num, fsConnection);
            }
        });

        runningFreeswitchs.keySet().forEach(num -> {
            ESLInboundHandler fsConnection = runningFreeswitchs.get(num);
            if (!fsConnection.isConnected()) {
                runningFreeswitchs.remove(num);
                pendingFreeswitchs.put(num, fsConnection);
                fsConnection.reconnect();
            }
        });
        logger.info("Finish checking ESL Inbound pending({}), running({})", pendingFreeswitchs.size(), runningFreeswitchs.size());
    }
}
