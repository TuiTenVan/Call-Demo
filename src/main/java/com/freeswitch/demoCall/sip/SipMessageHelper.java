package com.freeswitch.demoCall.sip;

import lombok.extern.log4j.Log4j2;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;

/**
 * https://svn.java.net/svn/jsip~svn/trunk/src/examples/
 */

@Log4j2
public class SipMessageHelper {

    public static HeaderFactory headerFactory;
    public static MessageFactory messageFactory;
    public static AddressFactory addressFactory;

    static {
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        try {
            addressFactory = sipFactory.createAddressFactory();
            headerFactory = sipFactory.createHeaderFactory();
            messageFactory = sipFactory.createMessageFactory();
        } catch (PeerUnavailableException ex) {
            log.error("PeerUnavailableException", ex);
        }
    }

}
