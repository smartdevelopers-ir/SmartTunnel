package ir.smartdevelopers.smarttunnel.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import ir.smartdevelopers.smarttunnel.BuildConfig;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class Logger {
    private static Set<MessageListener> messageListeners = new HashSet<>();
    public static void logInfo(String message){
        if (BuildConfig.DEBUG) {
            Log.i("TTT info", message);
        }
    }
    public static void logDebug(String message){
        if (BuildConfig.DEBUG){
            Log.d("TTT debug",message);
        }
    }
    private static WeakReference<Context> weakContext;

    public static void setContext(Context context){
        weakContext = new WeakReference<>(context);
    }
    public static Context getContext(){
        if (weakContext == null) {
            return null;
        }
        return weakContext.get();
    }

    public static void logError(String message) {
        if (TextUtils.isEmpty(message)){
            return;
        }
        Log.e("TTT error",message);
    }

    @SuppressLint("DefaultLocale")
    public static void logPacket(String toDest, Packet packet){
//        String proto;
//        if (packet.getTransmissionProtocol() instanceof UDP){
//            proto = "UDP";
//        }else {
//            proto = "TCP";
//        }
//        Logger.logDebug(String.format("%s - [%s] from %s:%d to %s:%d",
//                toDest,
//                proto,
//                ByteUtil.getAddressName(packet.getSourceAddress()),
//                ByteUtil.getIntValue(packet.getSourcePort()),
//                ByteUtil.getAddressName(packet.getDestAddress()),
//                ByteUtil.getIntValue(packet.getDestPort())
//        ));
    }

    public static void logException(Throwable e) {
        if (e.getMessage() == null){
            return;
        }
        Log.e("TTT",e.getMessage());
    }
    public static void logException(String message ,Throwable e) {
        if (e.getMessage() == null){
            return;
        }
        Log.e("TTT",message,e);
    }

    public static void logWarning(String message) {
        if (message == null){
            return;
        }
        Log.w("TTT",message);
    }
    public static void logMessage(LogItem item){
        if (weakContext.get() == null){
            return;
        }
        Util.MAIN_HANDLER.post(()->{
            for (MessageListener l : messageListeners){
                l.onNewMessage(item);
            }
        });
        PrefsUtil.addLog(weakContext.get(),item);
    }
    public static void logStyledMessage(String message, String color, boolean bold){
        if (weakContext.get() == null){
            return;
        }
        StringBuilder messageText=new StringBuilder();
        if (bold){
            messageText.append("<b>");
        }
        messageText.append("<font color='").append(color).append("'>")
                .append(message).append("</font>");
        if (bold){
            messageText.append("</b>");
        }
        logMessage(new LogItem(messageText.toString()));

    }

    public static void registerMessageListener(MessageListener listener){
        messageListeners.add(listener);
    }
    public static void unregisterMessageListener(MessageListener listener){
        messageListeners.remove(listener);
    }
    public interface MessageListener{
        void onNewMessage(LogItem item);
    }
}
