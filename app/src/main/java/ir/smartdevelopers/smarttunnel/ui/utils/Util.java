package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import com.jcraft.jsch.jce.KeyPairGenECDSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import de.blinkt.openvpn.core.NetworkUtils;
import ir.smartdevelopers.smarttunnel.SmartTunnelApp;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;

public class Util {
    public static ExecutorService SINGLE_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    public static Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    public static boolean contains(CharSequence holeText,CharSequence text){
        if (TextUtils.isEmpty(holeText)){
            return false;
        }
        if (text == null){
            return false;
        }
        return holeText.toString().toLowerCase().contains(text.toString().toLowerCase());
    }
    public static byte[] encrypt(String value){
        byte[] encrypted=null;
        try {
            String key = "adFElpssA51155asFGLo536Rt656zzpo";
            Key secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES");
            Cipher cipher = javax.crypto.Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE,secretKeySpec);
            encrypted = cipher.doFinal(value.getBytes());
        } catch (Exception e) {
            return null;
        }
        return encrypted;
    }
    public static String decrypt(byte[] data){
        byte[] decrypted=null;
        try {
            String key = "adFElpssA51155asFGLo536Rt656zzpo";
            Key secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE,secretKeySpec);
            decrypted = cipher.doFinal(data);
        } catch (Exception e) {
            return null;
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    public static List<LogItem> getDeviceInfoLogs(Context context){
        List<LogItem> logs = new ArrayList<>();
        String deviceManufactory = String.format("Running on %s (%s)",Build.MANUFACTURER,Build.MODEL);
        logs.add(new LogItem(deviceManufactory));
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();
        String networkName = networkInfo.getTypeName();
        String networkLogText = String.format("Connected to %s",networkName);
        logs.add(new LogItem(networkLogText));
        if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String simName = telephonyManager.getNetworkOperatorName();
            logs.add(new LogItem(String.format("Carrier name : %s",simName)));
        }
        String androidVersion = Build.VERSION.RELEASE;
        logs.add(new LogItem(String.format("Android version : %s",androidVersion)));



        return logs;
    }

    public static void setStatusBarMargin(View view){
        getStatusbarHeight(view, new OnCompleteListener<Integer>() {
            @Override
            public void onComplete(Integer integer) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                params.topMargin = SmartTunnelApp.mStatusBarHeight;
                view.requestLayout();
            }
        });
    }
    public static void setStatusBarPaddingToView(View view){
        getStatusbarHeight(view, new OnCompleteListener<Integer>() {
            @Override
            public void onComplete(Integer integer) {
                view.setPadding(view.getPaddingLeft(),view.getPaddingTop() + integer,view.getPaddingRight(),view.getPaddingBottom());
            }
        });

    }
    private static void getStatusbarHeight(View view,OnCompleteListener<Integer> callback){
        if (SmartTunnelApp.mStatusBarHeight == 0){
            view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    WindowInsetsCompat compat = new WindowInsetsCompat(WindowInsetsCompat.toWindowInsetsCompat(insets));
                    SmartTunnelApp.mStatusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    callback.onComplete(SmartTunnelApp.mStatusBarHeight);
                    return insets;
                }
            });
            return;
        }else {
            callback.onComplete(SmartTunnelApp.mStatusBarHeight);
        }
    }

    public static Uri getRawUri(Context context,int rawId){
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + rawId);
    }
}
