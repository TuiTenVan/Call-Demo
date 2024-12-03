package com.freeswitch.demoCall.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@org.springframework.context.annotation.Configuration
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Configuration {

    @Value("${ringme.freeswitch.els.ips}")
    public String[] freeswitchElsIPs;

    @Value("${ringme.freeswitch.els.port}")
    public int freeswitchElsPort;

    @Value("${ringme.domain.xmpp}")
    public String domainXmpp;

    @Value("${ringme.cms.noti.topic}")
    public String notiTopic;
}
