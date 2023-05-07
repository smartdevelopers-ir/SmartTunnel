package ir.smartdevelopers.smarttunnel;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.support.multidex.BuildConfig;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Executors;

import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class SmartTunnelApp extends Application {
    public static int mStatusBarHeight = 0;
    private final String[] defaultSelectedApps = {
            "com.android.vending",
            "com.google.android.gms",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.instagram.android",
    };
    @Override
    public void onCreate() {

        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            enableStrictModes();
        }
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                try{
                    Calendar calendar = Calendar.getInstance();
                    String name = String.format(Locale.ENGLISH,"%tY-%tm-%td",calendar,calendar,calendar);
                    File logFolder = getApplicationContext().getExternalFilesDir("logs");
                    File logFile = new File(logFolder,name+".log");
                    OutputStreamWriter fw = new FileWriter(logFile) ;
                    fw.append(Arrays.toString(e.getStackTrace()));
                    fw.close();

                } catch (Exception ex) {
                    //ignore
                }
            }
        });
        super.onCreate();
//        StatusListener mStatus = new StatusListener();
//        mStatus.init(getApplicationContext());
        createNotificatonChannel();
        if (Logger.getContext() == null){
            Logger.setContext(getApplicationContext());
        }
        if (PrefsUtil.isFirstTime(getApplicationContext())){
            PrefsUtil.setSelectedApps(getApplicationContext(),new HashSet<>(Arrays.asList(defaultSelectedApps)));
            PrefsUtil.setFirstTime(getApplicationContext(),false);
        }

    }

    private void createNotificatonChannel() {
        NotificationChannelCompat.Builder channelBuilder = new NotificationChannelCompat
                .Builder(MyVpnService.NOTIFICATION_CHANNEL_BG_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT);
        NotificationChannelCompat channelCompat = channelBuilder.setName(getString(R.string.notification_bg_name))
                .setDescription(getString(R.string.notification_bg_description))
                .build();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(getApplicationContext());
        managerCompat.createNotificationChannel(channelCompat);
    }
    private void enableStrictModes() {
        StrictMode.ThreadPolicy.Builder tpbuilder = new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog();



        StrictMode.VmPolicy.Builder vpbuilder = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            tpbuilder.penaltyListener(Executors.newSingleThreadExecutor(), this::logViolation);
            vpbuilder.penaltyListener(Executors.newSingleThreadExecutor(), this::logViolation);

        }
        //tpbuilder.penaltyDeath();
        //vpbuilder.penaltyDeath();

        StrictMode.VmPolicy policy = vpbuilder.build();
        StrictMode.setVmPolicy(policy);

    }
    @RequiresApi(api = Build.VERSION_CODES.P)
    public void logViolation(Violation v) {
        String name = Application.getProcessName();
        System.err.println("------------------------- Violation detected in " + name + " ------" + v.getCause() + "---------------------------");
        Logger.logError(v.getLocalizedMessage());
    }

}
