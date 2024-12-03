package com.freeswitch.demoCall.utils;

import com.freeswitch.demoCall.sip.UserSession;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.UUID;

public class XmppStanzaUtil {

    public static final String CALL_PSTN_JID = "callout@call.";
    public static Message makeXmlUserNotValid(String to, String sessionId, int type, String domain) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(XmppStanzaUtil.CALL_PSTN_JID + domain)
                    .to(to)
                    .ofType(Message.Type.chat)
                    .setBody("callout")
                    .build();

            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callout")
                    .build();
            message.addExtension(extContentType);

            StandardExtensionElement extData = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:callout:data")
                    .addAttribute("code", String.valueOf(type))
                    .build();

            StandardExtensionElement extCalldata = StandardExtensionElement.builder(
                            "callout", "urn:xmpp:ringme:callout")
                    .addElement(extData)
                    .addElement("session", sessionId)
                    .build();
            message.addExtension(extCalldata);

            StandardExtensionElement extNoStore = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();
            message.addExtension(extNoStore);
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    public static Message makeXmlBye(UserSession userSession, String duration, String domain) {
        Message message;
        try {
            message = MessageBuilder.buildMessage(UUID.randomUUID().toString())
                    .from(XmppStanzaUtil.CALL_PSTN_JID + domain)
                    .to(userSession.getFrom())
                    .ofType(Message.Type.chat)
                    .setBody("callout")
                    .build();

            StandardExtensionElement extContentType = StandardExtensionElement.builder(
                            "contentType", "urn:xmpp:ringme:contentType")
                    .addAttribute("name", "callout")
                    .build();
            message.addExtension(extContentType);

            StandardExtensionElement extData = StandardExtensionElement.builder(
                            "data", "urn:xmpp:ringme:callout:data")
                    .addAttribute("code", String.valueOf(203))
                    .addAttribute("duration", String.valueOf(203))
                    .build();

            StandardExtensionElement extCalldata = StandardExtensionElement.builder(
                            "callout", "urn:xmpp:ringme:callout")
                    .addElement(extData)
                    .addElement("session", userSession.getSessionId())
                    .build();
            message.addExtension(extCalldata);

            StandardExtensionElement extNoStore = StandardExtensionElement.builder(
                            "no-store", "urn:xmpp:hints")
                    .build();
            message.addExtension(extNoStore);
        } catch (XmppStringprepException e) {
            throw new RuntimeException(e);
        }
        return message;
    }
}
