package com.freeswitch.demoCall.service.inbound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.*;


public class LeaderElection implements Watcher {
    private final Logger logger = LogManager.getLogger(LeaderElection.class);

    private Object obj;
    private boolean isStartESLInbound;
    private ESLInboundManager eslInboundManager;

    public void setEslInboundManager(ESLInboundManager eslInboundManager) {
        this.obj = "";
        this.isStartESLInbound = false;
        this.eslInboundManager = eslInboundManager;
    }
    @Override
    public void process(WatchedEvent watchedEvent) {

    }
    public void reelectLeader() {
        eslInboundManager.startAllESLInbound();
    }
}
