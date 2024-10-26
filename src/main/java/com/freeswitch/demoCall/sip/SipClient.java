package com.freeswitch.demoCall.sip;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.*;

import java.util.*;

public class SipClient implements SipListener {

    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;

    private final String server;
    private final int port;

    private String username;
    private String password;

    public SipClient(String server, int port) throws Exception {
        this.server = server;
        this.port = port;
        init();
    }

    public void init() throws Exception {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SIPClient");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "sipclient_debug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "sipclient_log.txt");

        sipStack = sipFactory.createSipStack(properties);
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();

        ListeningPoint udp = sipStack.createListeningPoint("127.0.0.1", 5060, "udp");
        sipProvider = sipStack.createSipProvider(udp);
        sipProvider.addSipListener(this);
    }

    public void register(String username, String password) throws Exception {
        this.username = username;
        this.password = password;

        // Tạo SIP URI cho người dùng
        SipURI sipURI = addressFactory.createSipURI(username, server + ":" + port);
        Address address = addressFactory.createAddress(sipURI);
        address.setDisplayName(username);
        ContactHeader contactHeader = headerFactory.createContactHeader(address);

        // Tạo Request URI
        SipURI requestURI = addressFactory.createSipURI(null, server);
        requestURI.setTransportParam("udp");

        // Tạo Via header
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader("127.0.0.1", 5060, "udp", null);
        viaHeaders.add(viaHeader);

        // Tạo Max-Forwards header
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // Tạo Request (REGISTER)
        Request request = messageFactory.createRequest(
                requestURI,
                Request.REGISTER,
                sipFactory.createHeaderFactory().createCallIdHeader(sipProvider.getNewCallId().getCallId()),
                headerFactory.createCSeqHeader(1L, Request.REGISTER),
                headerFactory.createFromHeader(address, "client"),
                headerFactory.createToHeader(address, null),
                viaHeaders,
                maxForwards
        );

        // Thêm Contact header
        request.addHeader(contactHeader);

        // Thêm Expires header
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(3600);
        request.addHeader(expiresHeader);

        // Gửi Request
        sipProvider.sendRequest(request);
    }

    public void makeCall(String destination) throws Exception {
        // Tạo SIP URI cho người nhận
        SipURI sipURI = addressFactory.createSipURI(null, server + ":" + port);
        sipURI.setUser(destination);
        sipURI.setTransportParam("udp");

        Address toAddress = addressFactory.createAddress(sipURI);
        toAddress.setDisplayName(destination);

        // Tạo Request URI
        SipURI requestURI = (SipURI) sipURI.clone();

        // Tạo From header
        SipURI fromURI = addressFactory.createSipURI(username, server);
        Address fromAddress = addressFactory.createAddress(fromURI);
        fromAddress.setDisplayName(username);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "client");

        // Tạo To header
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // Tạo Call-ID
        CallIdHeader callIdHeader = sipProvider.getNewCallId();

        // Tạo CSeq header
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

        // Tạo Via headers
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader("127.0.0.1", 5060, "udp", null);
        viaHeaders.add(viaHeader);

        // Tạo Max-Forwards header
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // Tạo Request (INVITE)
        Request request = messageFactory.createRequest(
                requestURI,
                Request.INVITE,
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwards
        );

        // Thêm Contact header
        SipURI contactURI = addressFactory.createSipURI(username, "127.0.0.1");
        contactURI.setPort(5070);
        Address contactAddress = addressFactory.createAddress(contactURI);
        contactAddress.setDisplayName(username);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        request.addHeader(contactHeader);

        // Thêm Content-Type và Content-Length headers nếu cần
        // ...

        // Gửi Request
        sipProvider.sendRequest(request);
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();

        System.out.println("Received request: " + method);

        if (method.equals(Request.INVITE)) {
            try {
                Response response = messageFactory.createResponse(200, request);
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = sipProvider.getNewServerTransaction(request);
                }
                sipProvider.sendResponse(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        System.out.println("Received response: " + status);

        if (status == 200) {
            // Xử lý response thành công (REGISTER hoặc INVITE)
            // ...
        } else {
            // Xử lý các mã lỗi
            // ...
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        System.out.println("Request timed out");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IO Exception occurred");
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        // Xử lý khi giao dịch kết thúc
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        // Xử lý khi dialog kết thúc
    }
}
