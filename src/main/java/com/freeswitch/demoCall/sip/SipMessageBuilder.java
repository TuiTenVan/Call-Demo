package com.freeswitch.demoCall.sip;

import com.freeswitch.demoCall.utils.SipUtil;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Authorization;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.MaxForwards;
import gov.nist.javax.sip.message.SIPRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sip.InvalidArgumentException;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicLong;


public class SipMessageBuilder {

    private static final Logger logger = LogManager.getLogger(SipMessageBuilder.class);

    private static final AtomicLong REGISTER_CSEQ_GEN = new AtomicLong(1);

    private static final AtomicLong OPTIONS_CSEQ_GEN = new AtomicLong(1);

//    public static Request buildRegisterRequest(String displayName, String publicIdentity, String transportWS, String domain) throws ParseException {
//        String branch = SipUtil.randomBranch();
//        String callId = SipUtil.randomCallId();
//
//        String contactPublicIdentity = publicIdentity.substring(0, publicIdentity.indexOf("@"));
//        String register = "REGISTER sip:" + domain + " SIP/2.0\r\n"
//                + "Via: SIP/2.0/" + transportWS.toUpperCase() + " df7jal23ls0d.invalid;branch=" + branch + ";rport\r\n"
//                + "From: \"" + displayName + "\"<" + publicIdentity + ">;tag=" + "1txGfSU75MZAavHiTd3m" + "\r\n"
//                + "To: \"" + displayName + "\"<" + publicIdentity + ">\r\n"
//                + "Contact: \"" + displayName + "\"<" + contactPublicIdentity + "@df7jal23ls0d.invalid;transport=" + transportWS.toLowerCase() + ">;expires=200\r\n"
//                + "Call-ID: " + callId + "\r\n"
//                + "CSeq: " + REGISTER_CSEQ_GEN.incrementAndGet() + " REGISTER\r\n"
//                + "Content-Length: 0\r\n"
//                + "Max-Forwards: 70\r\n"
//                + "Supported: path\r\n"
//                + "\r\n";
//
//
//        return SipMessageHelper.messageFactory.createRequest(register);
//    }


    public static Request buildOptionRequest(String displayName, String publicIdentity, String transportWS, String domain) {
        Request request = null;
        try {
            String branch = SipUtil.randomBranch();
            String callId = SipUtil.randomCallId();

            String contactPublicIdentity = publicIdentity.substring(0, publicIdentity.indexOf("@"));
            String options = "OPTIONS sip:" + domain + " SIP/2.0\r\n"
                    + "Via: SIP/2.0/" + transportWS.toUpperCase() + " " + domain + ";branch=" + branch + ";rport\r\n"
                    + "From: \"" + displayName + "\" <" + publicIdentity + ">;tag=" + "1txGfSU75MZAavHiTd3m" + "\r\n"
                    + "To: " + "<" + "sip:" + domain + ">\r\n"
                    + "Contact: \"" + displayName + " \"<" + contactPublicIdentity + "@df7jal23ls0d.invalid;transport=" + transportWS.toLowerCase() + ">\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: " + OPTIONS_CSEQ_GEN.incrementAndGet() + " OPTIONS\r\n"
                    + "Content-Length: 0\r\n"
                    + "Accept: application/sdp\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "\r\n";
//			String options = "OPTIONS sip:" + domain + " SIP/2.0\r\n" +
//					"Via: SIP/2.0/" + transportWS.toUpperCase() + " " + domain + ";branch=" + branch + ";rport\r\n" +
//					"From: \"" + displayName + "\" <" + publicIdentity + ">;tag=" + "1txGfSU75MZAavHiTd3m" + "\r\n" +
//					"To: " + "<" + "sip:" + domain + ">\r\n" +
//					"Contact: \"" + displayName + "\" <" + contactPublicIdentity + "@df7jal23ls0d.invalid;transport=" + transportWS.toLowerCase() + ">\r\n" +
//					"Call-ID: " + callId + "\r\n" +
//					"CSeq: " + OPTIONS_CSEQ_GEN.incrementAndGet() + " OPTIONS\r\n" +
//					"Content-Length: 0\r\n" +
//					"Accept: application/sdp\r\n" +
//					"Max-Forwards: 70\r\n"  + "\r\n";
            System.out.println("MESSAGE: {}" + options);
//			logger.info("MESSAGE: {}", options);
            request = SipMessageHelper.messageFactory.createRequest(options);
        } catch (Exception e) {
            logger.error("buildOptionRequest Error: " + e.getMessage(), e);
        }

        return request;
    }

    public static Request buildInviteRequest(String displayName, String publicIdentity, String transportWS, String sipUri, String sdp, String callID, String sipGwAddress) throws ParseException {
        int CSeq = (int) (System.currentTimeMillis() / 1000) - 1491191270;

//		String callID = SipUtil.randomCallId();
        String fromTag = SipUtil.randomTag();
        String branch = SipUtil.randomBranch();

        String contactPublicIdentity = publicIdentity.substring(0, publicIdentity.indexOf("@"));

        String msg = "INVITE " + sipUri + " SIP/2.0\r\n"
                + "Via: SIP/2.0/" + transportWS.toUpperCase() + " " + sipGwAddress + ";branch=" + branch + ";rport\r\n"
                + "From: \"" + displayName + "\"<" + publicIdentity + ">;tag=" + fromTag + "\r\n"
//                + "From: <" + publicIdentity + ">;tag=" + fromTag + "\r\n"
                + "To: <" + sipUri + ">\r\n"
                + "Contact: \"" + displayName + "\"<" + contactPublicIdentity + "@" + sipGwAddress + ";transport=" + transportWS.toLowerCase() + ">\r\n"
//                + "Contact: \"" + displayName + "\"<" + publicIdentity + ";transport=" + transportWS.toLowerCase() + ">\r\n"
                + "Call-ID: " + callID + "\r\n"
                + "CSeq: " + CSeq + " INVITE\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + sdp.length() + "\r\n"
                + "Max-Forwards: 70\r\n"
                + "\r\n"
                + sdp;
        return SipMessageHelper.messageFactory.createRequest(msg);
    }

    public static Response build200OkResponse(Request request) throws ParseException {

//        logger.info(request.getRequestURI() + "-" + request.getSIPVersion());
        String msg = "SIP/2.0 200 OK\n" +
                request.getHeader("Via")  +
                request.getHeader("From") +
                request.getHeader("To") +
                request.getHeader("Call-ID") +
                request.getHeader("CSeq") +
//                "User-Agent: FreeSWITCH-mod_sofia/1.10.0~64bit\n" +
                "Allow: INVITE, ACK, BYE, CANCEL, OPTIONS, MESSAGE, INFO, UPDATE, REGISTER, REFER, NOTIFY\n" +
                "Supported: timer, path, replaces\n" +
                "Content-Length: 0\n";
        return SipMessageHelper.messageFactory.createResponse(msg);
    }
//    public static Request buildTryingRequest(String displayName, String publicIdentity, String transportWS, String sipUri, String callID) throws ParseException {
//        int CSeq = (int) (System.currentTimeMillis() / 1000) - 1491191270;
//
////		String callID = SipUtil.randomCallId();
//        String fromTag = SipUtil.randomTag();
//        String branch = SipUtil.randomBranch();
//
//        String contactPublicIdentity = publicIdentity.substring(0, publicIdentity.indexOf("@"));
//
//        String msg = "Trying " + sipUri + " SIP/2.0\r\n" +
//                "Via: SIP/2.0/" + transportWS.toUpperCase() + " df7jal23ls0d.invalid;branch=" + branch + ";rport\r\n" +
//                "From: \"" + displayName + "\"<" + publicIdentity + ">;tag=" + fromTag + "\r\n" +
//                "To: <" + sipUri + ">\r\n" +
//                "Contact: \"" + displayName + "\"<" + contactPublicIdentity + "@df7jal23ls0d.invalid;transport=" + transportWS.toLowerCase() + ">\r\n" +
//                "Call-ID: " + callID + "\r\n" +
//                "CSeq: " + CSeq + " INVITE\r\n" +
//                "Content-Length: 0\r\n" +
//                "\r\n" +
//                "";
//
//        logger.info(msg);
//        return SipMessageHelper.messageFactory.createRequest(msg);
//    }

//    public static Request buildRingingRequest(String displayName, String publicIdentity, String transportWS, String sipUri, String callID) throws ParseException {
//        int CSeq = (int) (System.currentTimeMillis() / 1000) - 1491191270;
//
////		String callID = SipUtil.randomCallId();
//        String fromTag = SipUtil.randomTag();
//        String branch = SipUtil.randomBranch();
//
//        String contactPublicIdentity = publicIdentity.substring(0, publicIdentity.indexOf("@"));
//
//        String msg = "Ringing " + sipUri + " SIP/2.0\r\n" +
//                "Via: SIP/2.0/" + transportWS.toUpperCase() + " df7jal23ls0d.invalid;branch=" + branch + ";rport\r\n" +
//                "From: \"" + displayName + "\"<" + publicIdentity + ">;tag=" + fromTag + "\r\n" +
//                "To: <" + sipUri + ">\r\n" +
//                "Call-ID: " + callID + "\r\n" +
//                "CSeq: " + CSeq + " INVITE\r\n" +
//                "Accept: application/sdp\r\n" +
//                "Allow: INVITE,ACK,BYE,CANCEL,OPTIONS,MESSAGE,INFO,UPDATE,REGISTER,REFER,NOTIFY,PUBLISH,SUBSCRIBE\r\n" +
//                "Supported: timer,path,replaces\r\n" +
//                "Allow-Events: talk,hold,conference,presence,as-feature-event,dialog,line-seize,call-info,sla,include-session-description,presence.winfo,message-summary,refer\r\n" +
//                "Content-Type: application/sdp\r\n" +
//                "Content-Length: 0\r\n" +
//                "Max-Forwards: 70\r\n" +
//                "\r\n" +
//                "";
//
//        logger.info(msg);
//        return SipMessageHelper.messageFactory.createRequest(msg);
//    }

//    public static Request build200Request(Request inviteRequest, Response response) {
//        SIPRequest okRequest = new SIPRequest();
//        okRequest.setMethod(Request.INVITE);
//
//        // RequestURI
//        okRequest.setRequestURI((SipUri) inviteRequest.getRequestURI().clone());
//
//        // Via
//        ViaHeader viaHeader = (ViaHeader) inviteRequest.getHeader(ViaHeader.NAME).clone();
//        // ...neu ack cho 2xx ... va >=3xx
//        if (response.getStatusCode() >= 200 && response.getStatusCode() <= 299) {
//            // tao branchID khac
//            String branchID = SipUtil.randomBranch();
//            try {
//                viaHeader.setBranch(branchID);
//            } catch (ParseException ex) {
//                logger.error("ParseException", ex);
//
//            }
//        }
//        okRequest.addHeader(viaHeader);
//        return null;
//    }

    public static SIPRequest buildAckRequest(Request inviteRequest, Response response) {
        /*
         * The ACK request constructed by the client transaction MUST contain
         * values for the Call-ID, From, and Request-URI that are equal to the
         * values of those header fields in the request passed to the transport
         * by the client transaction (call this the "original request").
         *
         * The To header field in the ACK MUST equal the To header field in the
         * response being acknowledged, and therefore will usually differ from
         * the To header field in the original request by the addition of the
         * tag parameter.
         *
         * The ACK MUST contain a single Via header field, and this MUST be
         * equal to the top Via header field of the original request.
         *
         * The CSeq header field in the ACK MUST contain the same value for the
         * sequence number as was present in the original request, but the
         * method parameter MUST be equal to "ACK".
         */
        SIPRequest ackRequest = new SIPRequest();
        ackRequest.setMethod(Request.ACK);

        // RequestURI
        ackRequest.setRequestURI((SipUri) inviteRequest.getRequestURI().clone());

        // Via
        ViaHeader viaHeader = (ViaHeader) inviteRequest.getHeader(ViaHeader.NAME).clone();
        // ...neu ack cho 2xx ... va >=3xx
        if (response.getStatusCode() >= 200 && response.getStatusCode() <= 299) {
            // tao branchID khac
            String branchID = SipUtil.randomBranch();
            try {
                viaHeader.setBranch(branchID);
            } catch (ParseException ex) {
                logger.error("ParseException", ex);

            }
        }
        ackRequest.addHeader(viaHeader);

        // CallID
        CallID callId = (CallID) (inviteRequest.getHeader(CallID.NAME)).clone();
        ackRequest.setCallId(callId);

        // From
        FromHeader from = (FromHeader) inviteRequest.getHeader(FromHeader.NAME).clone();
        ackRequest.setFrom(from);

        // To
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME).clone();
        ackRequest.setTo(to);

        // CSeq
        CSeqHeader cSeqHeader = (CSeqHeader) inviteRequest.getHeader(CSeqHeader.NAME);
        ackRequest.setCSeq(new CSeq(cSeqHeader.getSeqNumber(), Request.ACK));

        // MaxForwards
        try {
            ackRequest.setMaxForwards(new MaxForwards(70));
        } catch (InvalidArgumentException ex) {
            logger.error("InvalidArgumentException", ex);
        }

        // Authorization
        Authorization authorization = (Authorization) inviteRequest.getHeader(Authorization.NAME);
        if (authorization != null) {
            ackRequest.addHeader(authorization);// 2xx thi can, 3xx-6xx co ve ko
            // can??
        }

        ProxyAuthorizationHeader proxyAuthorizationHeader = (ProxyAuthorizationHeader) inviteRequest
                .getHeader(ProxyAuthorizationHeader.NAME);
        if (proxyAuthorizationHeader != null) {// 2xx thi can, 3xx-6xx co ve ko
            // can??
            ackRequest.addHeader(proxyAuthorizationHeader);
        }

        return ackRequest;
    }

    public static SIPRequest buildByeRequest(Request inviteRequest, Response finalResponse) {
        SIPRequest byeRequest = new SIPRequest();
        byeRequest.setMethod(Request.BYE);

        ContactHeader contactHeader = (ContactHeader) finalResponse.getHeader(ContactHeader.NAME);
        if (contactHeader == null) {
            logger.error("Contact Header on Final Response NULL: " + finalResponse);
        } else {
            byeRequest.setRequestURI((URI) contactHeader.getAddress().getURI().clone());
        }

        // Via
        ViaHeader viaHeader = (ViaHeader) inviteRequest.getHeader(ViaHeader.NAME).clone();
        try {
            viaHeader.setBranch(SipUtil.randomBranch());
        } catch (ParseException ex) {
            logger.error("ParseException", ex);
        }
        byeRequest.addHeader(viaHeader);

        // CallID
        CallID callId = (CallID) (inviteRequest.getHeader(CallID.NAME)).clone();
        byeRequest.setCallId(callId);

        FromHeader from = (FromHeader) finalResponse.getHeader(FromHeader.NAME).clone();
        byeRequest.setFrom(from);

        // To
        ToHeader to = (ToHeader) finalResponse.getHeader(ToHeader.NAME).clone();
        byeRequest.setTo(to);

        // MaxForwards
        try {
            byeRequest.setMaxForwards(new MaxForwards(70));
        } catch (InvalidArgumentException ex) {
            logger.error("InvalidArgumentException", ex);
        }

        // CSeq
        CSeqHeader cSeqHeader = (CSeqHeader) inviteRequest.getHeader(CSeqHeader.NAME);
        byeRequest.setCSeq(new CSeq(cSeqHeader.getSeqNumber() + 20, Request.BYE));

        return byeRequest;
    }

    public static SIPRequest buildCancelRequest(Request inviteRequest) {
        // https://tools.ietf.org/html/rfc3261#section-9
        /*
         * The Request-URI, Call-ID, To, the numeric part of CSeq, and From
         * header fields in the CANCEL request MUST be identical to those in the
         * request being cancelled, including tags.
         */

        SIPRequest cancelRequest = new SIPRequest();
        cancelRequest.setMethod(Request.CANCEL);

        // RequestURI = Contact final res
        URI requestUri = (URI) inviteRequest.getRequestURI().clone();
        cancelRequest.setRequestURI(requestUri);

        // Via
        ViaHeader viaHeader = (ViaHeader) inviteRequest.getHeader(ViaHeader.NAME).clone();
        cancelRequest.addHeader(viaHeader);

        // From
        FromHeader from = (FromHeader) inviteRequest.getHeader(FromHeader.NAME).clone();
        cancelRequest.setFrom(from);

        // To
        ToHeader to = (ToHeader) inviteRequest.getHeader(ToHeader.NAME).clone();
        cancelRequest.setTo(to);

        // CallID
        CallID callId = (CallID) (inviteRequest.getHeader(CallID.NAME)).clone();
        cancelRequest.setCallId(callId);

        // CSeq
        CSeqHeader cSeqHeader = (CSeqHeader) inviteRequest.getHeader(CSeqHeader.NAME);
        cancelRequest.setCSeq(new CSeq(cSeqHeader.getSeqNumber(), Request.CANCEL));

        // MaxForwards
        try {
            cancelRequest.setMaxForwards(new MaxForwards(70));
        } catch (InvalidArgumentException ex) {
            logger.error("InvalidArgumentException", ex);
        }
        //logger.info("BUILD_CANCEL_REQUEST: " + cancelRequest.toString());
        return cancelRequest;
    }

    public static Request buildInfoRequest(Request inviteRequest, String dtmf) throws ParseException {

        CSeqHeader cSeqHeader = (CSeqHeader) inviteRequest.getHeader(CSeqHeader.NAME);

        String sdp = "Signal=" + dtmf + "\r\nDuration=300";

        String msg = Request.INFO + " " + inviteRequest.getRequestURI().toString() + " SIP/2.0\r\n"
                + inviteRequest.getHeader(ViaHeader.NAME).toString()
                + inviteRequest.getHeader(FromHeader.NAME).toString()
                + inviteRequest.getHeader(ToHeader.NAME).toString()
                + inviteRequest.getHeader(CallID.NAME).toString()
                + new CSeq(cSeqHeader.getSeqNumber(), Request.INFO)
                + "Content-Type: application/dtmf-relay\r\n"
                + "Content-Length: " + sdp.length() + "\r\n"
                + "Max-Forwards: 70\r\n"
                + "\r\n"
                + sdp;

        logger.info(msg);

        return SipMessageHelper.messageFactory.createRequest(msg);
    }
}
