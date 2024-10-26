package com.freeswitch.demoCall.utils;


import com.freeswitch.demoCall.common.Key;
import io.pkts.buffer.Buffer;
import io.pkts.buffer.Buffers;
import io.pkts.buffer.ByteNotFoundException;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipRequest;
import io.pkts.packet.sip.SipResponse;
import io.pkts.packet.sip.address.SipURI;
import io.pkts.packet.sip.header.*;
import io.pkts.packet.sip.header.impl.CSeqHeaderImpl;
import io.pkts.packet.sip.header.impl.FromHeaderImpl;
import io.pkts.packet.sip.header.impl.MaxForwardsHeaderImpl;
import io.pkts.packet.sip.header.impl.ToHeaderImpl;
import io.pkts.packet.sip.impl.SipParser;
import io.pkts.packet.sip.impl.SipRequestImpl;
import io.pkts.packet.sip.impl.SipRequestLine;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class SipUtil {

    private static final Random RANDOM = new Random();
    private static final String CHARACTERS = "abcdefghijkmnpqrstxyz1234567890";

    /**
     * de dam bao random tag, callID, branch khong trung nhau
     */
    private static final AtomicLong TMP_GENERATOR = new AtomicLong(0);

    public static String randomString(int length) {
        char[] text = new char[length];
        for (int i = 0; i < length; i++) {
            text[i] = CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length()));
        }
        return new String(text);
    }

    public static String randomTag() {
        return randomString(20) + "." + TMP_GENERATOR.incrementAndGet();
    }

    public static String randomCallId() {
        return randomString(28) + "." + TMP_GENERATOR.incrementAndGet();
    }

    public static String randomBranch() {
        return "z9hG4bK." + randomString(28) + "." + TMP_GENERATOR.incrementAndGet();
    }

    public static final String getFromUser(SipMessage req) {
        final SipURI uri = (SipURI) req.getFromHeader().getAddress().getURI();
        return uri.getUser().toString();
    }

    public static final String getToUser(SipMessage req) {
        final SipURI uri = (SipURI) req.getToHeader().getAddress().getURI();
        return uri.getUser().toString();
    }

    public static final String getCallID(SipMessage req) {
        return req.getCallIDHeader().getCallId().toString();
    }

    public static final String getGetwayName(SipMessage req) {
        ContactHeader c = req.getContactHeader();
        String values = c.getValue().clone().toString();
        if (StringUtils.isNotEmpty(values)) {
            String raw = values.replaceFirst("<", "").replaceAll(">", "");
            String[] args = raw.split(";");
            for (String e : args) {
                if (e.startsWith("gw=")) {
                    return e.substring(3);
                }
            }
        }
        return null;
    }

    public static final String getReason(SipMessage req) {
        Buffer buffer = req.getHeader("Reason").getValue().clone();
        try {
            buffer.readUntil((byte) '\"');
            return buffer.readUntil((byte) '\"').toString();
        } catch (ByteNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return "NO_REASON";
    }

    public static final SipRequest makeByeRequest(SipMessage req) {
        ContactHeader contact = req.getContactHeader();
        SipURI requestURI = (SipURI) contact.getAddress().getURI();
        final FromHeader from = req.getFromHeader();
        final ToHeader to = req.getToHeader();

        FromHeader from2To = new FromHeaderImpl(to.getAddress(), Buffers.wrap(";tag=" + to.getParameter("tag")));
        ToHeader to2From = new ToHeaderImpl(from.getAddress(), Buffers.wrap(";tag=" + from.getParameter("tag")));

        final CallIdHeader callId = req.getCallIDHeader();
        final CSeqHeader cseq = new CSeqHeaderImpl(req.getCSeqHeader().getSeqNumber() + 20, Buffers.wrap("BYE"), null);
        final ViaHeader via = req.getViaHeader();

        final SipRequestLine initialLine = new SipRequestLine(Buffers.wrap("BYE"), requestURI);
        final SipRequest request = new SipRequestImpl(initialLine, null, null);
        request.setHeader(from2To);
        request.setHeader(to2From);
        request.setHeader(cseq);
        request.setHeader(callId);
        request.setHeader(new MaxForwardsHeaderImpl(70));
        via.setBranch(Buffers.wrap("z9hG4bK-" + UUID.randomUUID()));
        request.addHeader(via);
        request.setHeader(contact);

        return request;
    }

    public static final SipResponse makeRingingRespSDP(int respCode, SipMessage req, String sdp)
            throws ClassCastException, IOException {
        SipResponse response = req.createResponse(respCode);
        String builder = response.toString() + Key.CONTENT_TYPE_KEY + ": " + "application/sdp" + Key.ENDLINE +
                Key.CONTENT_LENGTH_KEY + ": " + sdp.length() + Key.ENDLINE +
                Key.ENDLINE +
                sdp;

        return SipParser.frame(Buffers.wrap(builder)).toResponse();
    }

    public static final SipResponse makeResponseSDP(SipMessage req, String sdp) throws ClassCastException, IOException {
        SipResponse response = req.createResponse(200);
        String builder = response.toString() + Key.CONTENT_TYPE_KEY + ": " + "application/sdp" + Key.ENDLINE +
                Key.CONTENT_LENGTH_KEY + ": " + sdp.length() + Key.ENDLINE +
                Key.ENDLINE +
                sdp;

        return SipParser.frame(Buffers.wrap(builder)).toResponse();
    }
}
