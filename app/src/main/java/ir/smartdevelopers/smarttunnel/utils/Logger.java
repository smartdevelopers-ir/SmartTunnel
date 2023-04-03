package ir.smartdevelopers.smarttunnel.utils;

import android.text.TextUtils;
import android.util.Log;

public class Logger {
    public static void logInfo(String message){

    }
    public static void logDebug(String message){

    }

    public static void logError(String message) {
        if (TextUtils.isEmpty(message)){
            return;
        }
        Log.e("TTT",message);
    }
}
