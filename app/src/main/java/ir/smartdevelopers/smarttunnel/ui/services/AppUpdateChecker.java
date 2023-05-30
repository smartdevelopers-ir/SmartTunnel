package ir.smartdevelopers.smarttunnel.ui.services;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import ir.smartdevelopers.smarttunnel.BuildConfig;
import ir.smartdevelopers.smarttunnel.ui.activities.MainActivity;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class AppUpdateChecker extends Worker {
    public AppUpdateChecker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String updateJson = getUpdateJson();
        if (updateJson == null){
            return Result.failure();
        }
        UpdateObject updateObject = new Gson().fromJson(updateJson,UpdateObject.class);
        if (BuildConfig.VERSION_CODE < updateObject.versionCode){
            PrefsUtil.setUpdateUrl(getApplicationContext(),updateObject.url);
            PrefsUtil.setUpdateVersionCode(getApplicationContext(),updateObject.versionCode);
            if (!TextUtils.isEmpty(updateObject.url)){
                notifyUpdateAvailable(updateObject.url);
            }
        }else{
            File downloadFolder = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadFolder.exists()){
                Util.deleteAllFiles(downloadFolder);
            }

        }
        return Result.success();
    }

    private void notifyUpdateAvailable(String url) {
        Intent intent = new Intent(MainActivity.ACTION_UPDATE_AVAILABLE);
        intent.putExtra("url",url);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private String getUpdateJson(){
        try {
            URLConnection connection=  new URL("https://raw.githubusercontent.com/smartdevelopers-ir/SmartTunnel/master/update").openConnection();
            connection.connect();

            InputStream inputStream = connection.getInputStream();
            byte[] buff = new byte[32*1024];
            int len =0;
            len = inputStream.read(buff);
            String json = new String(buff,0,len);
            inputStream.close();
            return json;
        }catch (Exception e){
            //ignore
        }
        return null;
    }
    public static void checkForUpdate(Context context){
        WorkManager manager = WorkManager.getInstance(context);
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(AppUpdateChecker.class);
        builder.setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build());
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest(builder);
        manager.beginUniqueWork("ir.smartdevelopers.smarttunnel.update_worker",
                ExistingWorkPolicy.REPLACE,workRequest).enqueue();
    }
    public static class UpdateObject {
        public String version;
        public int versionCode;
        public String url;
    }
}
