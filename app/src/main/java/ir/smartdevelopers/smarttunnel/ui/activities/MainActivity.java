package ir.smartdevelopers.smarttunnel.ui.activities;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.SmartTunnelApp;
import ir.smartdevelopers.smarttunnel.ui.fragments.AppsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.ConfigsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.HomeFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.LogFragment;
import ir.smartdevelopers.smarttunnel.ui.services.AppUpdateChecker;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_UPDATE_AVAILABLE = "ir.smartdevelopers.smarttunnel.ACTION_UPDATE_AVAILABLE";
    private BottomNavigationView mBottomNavigationView;
    private BroadcastReceiver mUpdateAvalablityListener;
    private BroadcastReceiver mDownloadCompleteReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initReceivers();
        mBottomNavigationView = findViewById(R.id.bottomNavigation);
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                WindowInsetsCompat compat = new WindowInsetsCompat(WindowInsetsCompat.toWindowInsetsCompat(insets));
                SmartTunnelApp.mStatusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                return insets;
            }
        });
        if (getIntent().getData() != null){
            Bundle args = new Bundle();
            args.putParcelable(ConfigsFragment.KEY_CONFIG_URI,getIntent().getData());
            goToConfigFragment(false,args);
        }

        mBottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(mBottomNavigationView.getSelectedItemId() == item.getItemId()){
                    return false;
                }
                if (item.getItemId() == R.id.action_home){
                    goToHomeFragment(true,null);
                }else if (item.getItemId() == R.id.action_config){
                    goToConfigFragment(true,null);
                } else if (item.getItemId() == R.id.action_log) {
                    goToLogFragment();
                } else if (item.getItemId() == R.id.action_apps) {
                    goToAppsFragment();
                }
                return true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!(getSupportFragmentManager().findFragmentById(R.id.mainFragmentContainer) instanceof HomeFragment)){
                    mBottomNavigationView.setSelectedItemId(R.id.action_home);
                    return;
                }
                setEnabled(false);
                onBackPressed();
            }
        });
        checkForUpdateAvailable();
    }

    private void checkForUpdateAvailable() {
        AppUpdateChecker.checkForUpdate(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mUpdateAvalablityListener);
        unregisterReceiver(mDownloadCompleteReceiver);
        super.onDestroy();
    }

    private void initReceivers() {
        mUpdateAvalablityListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_UPDATE_AVAILABLE.equals(intent.getAction())){
                    String url = intent.getStringExtra("url");
                    showUpdateDialog(url);
                }
            }
        };
        IntentFilter updateAvailableFilter = new IntentFilter(ACTION_UPDATE_AVAILABLE);
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mUpdateAvalablityListener,updateAvailableFilter);
        mDownloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                 if (intent != null && DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,0);
                    if (id != 0){
                        openDownloadedApk(id);
                    }
                }
            }
        };
        IntentFilter downloadCompleteFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(mDownloadCompleteReceiver,downloadCompleteFilter);

    }

    private long downloadedId ;
    private void openDownloadedApk(long id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!getPackageManager().canRequestPackageInstalls()){
                downloadedId = id;
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse(String.format("package:%s", getPackageName()))), 100);
                return;
            }
        }
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri uri = downloadManager.getUriForDownloadedFile(id);
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(uri,"application/vnd.android.package-archive");
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(installIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 ){
            if (resultCode == RESULT_OK){
                openDownloadedApk(downloadedId);
            }
        }
    }

    private void showUpdateDialog(String url) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setMessage(R.string.update_available_message)
                .setTitle(R.string.update_available_title)
                .setIcon(R.drawable.ic_update)
                .setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startUpdate(url);
                    }
                })
                .setNegativeButton(R.string.cancel,null)
                .show();
    }

    private void startUpdate(String url) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription(getString(R.string.download_update_descriptin));
        request.setTitle(getString(R.string.app_name));
        request.setVisibleInDownloadsUi(true);
        long id = downloadManager.enqueue(request);
    }


    private void goToAppsFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new AppsFragment())
                .commit();
    }

    private void goToLogFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new LogFragment())
                .commit();
    }

    private void goToConfigFragment(boolean animate,Bundle data) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,ConfigsFragment.class,data)
                .commit();
    }

    private void goToHomeFragment(boolean animate,Bundle data) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,HomeFragment.class,data)
                .commit();
    }


}