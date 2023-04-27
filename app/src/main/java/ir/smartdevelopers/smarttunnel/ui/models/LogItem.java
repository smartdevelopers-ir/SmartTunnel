package ir.smartdevelopers.smarttunnel.ui.models;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import com.google.gson.Gson;

public class LogItem {
    public static final int MAX_LOG_CACHE_SIZE = 100;
    public String logRawText;
    public long timeStamp;

    public LogItem(String logRawText) {
        this.logRawText = logRawText;
        timeStamp = System.currentTimeMillis();
    }

    public LogItem(String logRawText, long timeStamp) {
        this.logRawText = logRawText;
        this.timeStamp = timeStamp;
    }
    public String getAsJson(){
        return new Gson().toJson(this);
    }
    public static LogItem fromJson(String json){
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        return new Gson().fromJson(json,LogItem.class);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("[%tH:%tM:%tS] %s",timeStamp,timeStamp,timeStamp, HtmlCompat.fromHtml(logRawText,HtmlCompat.FROM_HTML_MODE_COMPACT));
    }
}
