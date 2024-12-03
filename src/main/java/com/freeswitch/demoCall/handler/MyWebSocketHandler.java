package com.freeswitch.demoCall.handler;

import com.freeswitch.demoCall.sip.SipClient;
import org.json.JSONObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class MyWebSocketHandler extends TextWebSocketHandler {

    private SipClient sipClient;

    public MyWebSocketHandler() {
        try {
            sipClient = new SipClient("192.168.1.70", 5070);
            sipClient.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("New WebSocket connection established");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received message: " + payload);

        JSONObject jsonObject = new JSONObject(payload);
        if (payload.contains("register")) {
            String username = (String) jsonObject.get("username");
            String password = (String) jsonObject.get("password");
            System.out.println("Registered user: " + username + " and password: " + password);
            sipClient.register(username, password);
            session.sendMessage(new TextMessage("Registered user: " + username));
        } else if (payload.contains("call")) {
            String destination = (String) jsonObject.get("destination");
            sipClient.makeCall(destination);
            session.sendMessage(new TextMessage("Calling: " + destination));
        } else {
            session.sendMessage(new TextMessage("Unknown action"));
        }
    }
}