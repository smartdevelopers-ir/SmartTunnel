package ir.smartdevelopers.smarttunnel.ui.services;

import android.app.IntentService;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class DeleteConfigFileService extends Worker {
    public DeleteConfigFileService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<ConfigListModel> configs = PrefsUtil.getAllConfigs(getApplicationContext());
        File configFolder = ConfigsUtil.getConfigsFolder(getApplicationContext());
        File[] folders = configFolder.listFiles();
        if (folders == null){
            return Result.success();
        }
        for (File dir : folders){
            if (dir.isDirectory()){
                File[] configsFiles = dir.listFiles();
                if (configsFiles!=null){
                    for (File f : configsFiles){
                        if (!containConfigFile(configs,f)){
                            f.delete();
                        }
                    }
                }
            }
        }
        return Result.success();
    }
    private boolean containConfigFile(List<ConfigListModel> configListModels,File configFile){
        for (ConfigListModel model : configListModels){
            if (Objects.equals(model.configId,configFile.getName())){
                return true;
            }
        }
        return false;
    }
    public static void scheduleWork(Context context,int delay){
        WorkManager manager = WorkManager.getInstance(context);
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(DeleteConfigFileService.class);
        builder.setInitialDelay(delay, TimeUnit.SECONDS);
        OneTimeWorkRequest request = builder.build();
        manager.beginUniqueWork("delete_config_file", ExistingWorkPolicy.REPLACE,request).enqueue();
    }
}
