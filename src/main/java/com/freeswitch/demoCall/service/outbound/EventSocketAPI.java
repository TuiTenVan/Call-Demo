package com.freeswitch.demoCall.service.outbound;

import lombok.extern.log4j.Log4j2;
import org.freeswitch.esl.client.internal.AbstractEslClientHandler;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.message.EslHeaders.Name;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

@Log4j2
public class EventSocketAPI {

    public static boolean setVariables(ChannelHandlerContext ctx, String variable) {
        SendMsg bridgeMsg = new SendMsg();
        bridgeMsg.addCallCommand("execute");
        bridgeMsg.addExecuteAppName("export");
        bridgeMsg.addExecuteAppArg(variable);
        bridgeMsg.addEventLock();

        EslMessage response = ((AbstractEslClientHandler) ctx.getHandler()).sendSyncMultiLineCommand(ctx.getChannel(),
                bridgeMsg.getMsgLines());

        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean setRecord(ChannelHandlerContext ctx) {
        SendMsg bridgeMsg = new SendMsg();
        bridgeMsg.addCallCommand("execute");
        bridgeMsg.addExecuteAppName("export");
//        bridgeMsg.addExecuteAppArg("execute_on_answer=record_session /call-record/${sip_call_id}.wav");
        bridgeMsg.addExecuteAppArg("execute_on_answer=record_session /etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav");
        bridgeMsg.addEventLock();

        EslMessage response = ((AbstractEslClientHandler) ctx.getHandler()).sendSyncMultiLineCommand(ctx.getChannel(),
                bridgeMsg.getMsgLines());

        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean bridgeCall(ChannelHandlerContext ctx, String appArg, Map<String, String> args) {
        SendMsg bridgeMsg = new SendMsg();
        bridgeMsg.addCallCommand("execute");
        bridgeMsg.addExecuteAppName("bridge");
        bridgeMsg.addExecuteAppArg(appArg);
        bridgeMsg.addEventLock();
        Iterator<String> keys = args.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = args.get(key);
            bridgeMsg.addGenericLine(key, value);
        }

        EslMessage response = ((AbstractEslClientHandler) ctx.getHandler()).sendSyncMultiLineCommand(ctx.getChannel(),
                bridgeMsg.getMsgLines());

        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean hangupCall(ChannelHandlerContext ctx) {
        SendMsg hangupMsg = new SendMsg();
        hangupMsg.addCallCommand("execute");
        hangupMsg.addExecuteAppName("hangup");

        EslMessage response = ((AbstractEslClientHandler) ctx.getHandler()).sendSyncMultiLineCommand(ctx.getChannel(),
                hangupMsg.getMsgLines());

        boolean isResult = response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
        if (!isResult) {
            log.error("Call hangup failed:" + response.getHeaderValue(Name.REPLY_TEXT));
        }
        return isResult;
    }

    public static boolean playAndGetDigit(ChannelHandlerContext ctx) {

        AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
        Channel channel = ctx.getChannel();

        SendMsg playAndGetDigitMsg = new SendMsg();
        playAndGetDigitMsg.addCallCommand("execute");
        playAndGetDigitMsg.addExecuteAppName("play_and_get_digits");
        playAndGetDigitMsg.addExecuteAppArg("0 9 3 3000 # voicemail/vm-enter_pass.wav");
//        playAndGetDigitMsg.addLoops(3);
        playAndGetDigitMsg.addEventLock();

        EslMessage response = handler.sendSyncMultiLineCommand(channel,
                playAndGetDigitMsg.getMsgLines());
        log.info("playAndGetDigit={}", response);
        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean playToneStream(ChannelHandlerContext ctx, String voicemail, int countLoop) {
        AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
        Channel channel = ctx.getChannel();

        SendMsg playBackMsg = new SendMsg();
        playBackMsg.addCallCommand("execute");
        playBackMsg.addExecuteAppName("playback");
//        playBackMsg.addExecuteAppArg("voicemail/" + voicemail);
        playBackMsg.addExecuteAppArg("/etc/freeswitch/call-record/sounds/" + voicemail);
        playBackMsg.addLoops(countLoop);
        playBackMsg.addEventLock();

        EslMessage response = handler.sendSyncMultiLineCommand(channel, playBackMsg.getMsgLines());
        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean stopToneStream(ChannelHandlerContext ctx) {
        AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
        Channel channel = ctx.getChannel();

        SendMsg cmd = new SendMsg();
        cmd.addCallCommand("execute");
        cmd.addExecuteAppName("break");

        EslMessage response = handler.sendSyncMultiLineCommand(channel, cmd.getMsgLines());

        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean runCommand(ChannelHandlerContext ctx, String appName, String appArg, boolean addEventLock) {
        AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
        Channel channel = ctx.getChannel();

        SendMsg cmd = new SendMsg();
        cmd.addCallCommand("execute");
        cmd.addExecuteAppName(appName);

        if ( appArg != null) {
            cmd.addExecuteAppArg(appArg);
        }

        if (addEventLock) {
            cmd.addEventLock();
        }
        EslMessage response = handler.sendSyncMultiLineCommand(channel, cmd.getMsgLines());
        log.info("runCommand={}|{}", appName + " " + appArg + " " + addEventLock, response.getBodyLines());
        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static boolean runCommandSet(ChannelHandlerContext ctx, String appName, String appArg) {
        AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
        Channel channel = ctx.getChannel();

        SendMsg cmd = new SendMsg();
        cmd.addCallCommand("set");
        cmd.addExecuteAppName(appName);

        if ( appArg != null) {
            cmd.addExecuteAppArg(appArg);
        }

        EslMessage response = handler.sendSyncMultiLineCommand(channel, cmd.getMsgLines());
        log.info("runCommandSet={}|{}", response.getHeaders(), response.getBodyLines());
        return response.getHeaderValue(Name.REPLY_TEXT).startsWith("+OK");
    }

    public static String makePlayAndGetDigitCommand(Integer min, Integer max, Integer maxFailures, Integer timeout,
                                                    String terminators, String file, String invalidFile, String varName,
                                                    String regex, Integer digitTimeout) {

        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(String.valueOf(min));
        joiner.add(String.valueOf(max));
        joiner.add(String.valueOf(maxFailures));
        joiner.add(String.valueOf(timeout));
        joiner.add(terminators);
        joiner.add(file != null ? file : "silence_stream://250");
        joiner.add(invalidFile != null ? invalidFile : "silence_stream://250");
        joiner.add(varName);
        joiner.add(regex);
        joiner.add(String.valueOf(digitTimeout));

        log.info(joiner.toString());
        return joiner.toString();
    }
}
