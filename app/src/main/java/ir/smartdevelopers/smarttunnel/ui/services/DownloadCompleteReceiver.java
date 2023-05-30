package ir.smartdevelopers.smarttunnel.ui.services;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class DownloadCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,0);
            if (id != 0){
                String url = PrefsUtil.getUpdateUrl(context);
                PrefsUtil.setDownloadedApkId(context,url,id);
            }
        }
    }
}
