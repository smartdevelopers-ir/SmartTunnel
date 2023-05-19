package ir.smartdevelopers.smarttunnel;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;

import java.util.Objects;

import de.blinkt.openvpn.core.NetworkUtils;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.receivers.NetworkStateReceiver;
import ir.smartdevelopers.smarttunnel.ui.activities.MainActivity;
import ir.smartdevelopers.smarttunnel.ui.exceptions.AuthFailedException;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;
import ir.smartdevelopers.smarttunnel.ui.models.ProxyType;
import ir.smartdevelopers.smarttunnel.ui.models.SSHProxy;
import ir.smartdevelopers.smarttunnel.ui.utils.AlertUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class MyVpnService extends VpnService implements NetworkStateReceiver.Callback {

    public static final int COMMAND_CONNECT = 1;
    public static final int COMMAND_DISCONNECT = 2;
    public static final int COMMAND_RECONNECT = 3;
    public static final int MODE_CONNECT= 200;
    public static final int MODE_RECONNECT= 201;
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    public static final String ACTION_CONNECTED = "connected";
    public static final String ACTION_DISCONNECTED = "disconnected";
    public static final String ACTION_CONNECTING = "connecting";
    public static final String ACTION_RETRYING = "retrying";
    public static final String ACTION_DISCONNECTING = "disconnecting";
    private static final int NOTIFICATION_ID = 50;
    public static final String NOTIFICATION_CHANNEL_BG_ID = "smart_tunnel_notification_bg";
    private static final String PRIVATE_VLAN4_CLIENT = "10.0.0.1";
    private static final String PRIVATE_VLAN4_ROUTER = "10.0.0.2";

    private static final String PRIVATE_VLAN6_CLIENT = "fc00::1";
    private static final String PRIVATE_VLAN6_ROUTER = "fc00::2";

    private static final String PRIVATE_NETMASK = "255.255.255.252";

    private static final int PRIVATE_MTU = 1500;
    private boolean mStopService;

    private final Object mConnectLock = new Object();
    private ConnectivityManager connectivityManager;
    private HandlerThread mServiceThread;
    private Handler mUiThreadHandler;
    private ServiceHandler mServiceHandler;
    private Config mCurrentConfig;
    private String mConfigId;
    private String mConfigType;
    private VpnBinder mBinder;
    private int retryWaitTime = 0;
    private int retryCount = 0;
    private NetworkStateReceiver mNetworkStateReceiver;
    /**
     * Connection status
     */
    public Status mStatus = Status.DISCONNECTED;

    private BroadcastReceiver mBroadcastReceiver;


    public enum Status {
        CONNECTED, CONNECTING, DISCONNECTED, NETWORK_ERROR,DISCONNECTING,RETRYING
    }

    public void reset(){
        mStopService = false;
        retryWaitTime = 0;
        retryCount = 0;
        mCurrentConfig = null;
        mConfigId = null;
        mConfigType = null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        mServiceThread = new HandlerThread("vpnServiceThread");
        mServiceThread.start();
        mServiceHandler = new ServiceHandler(mServiceThread.getLooper());
        mUiThreadHandler = new Handler(Looper.getMainLooper());
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mBinder = new VpnBinder(this);
        initBroadcast();
        if (Logger.getContext() == null) {
            Logger.setContext(getApplicationContext());
        }
        mNetworkStateReceiver = new NetworkStateReceiver(this);
        registerDeviceStateReceiver(mNetworkStateReceiver);


    }

    private void initBroadcast() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };
        IntentFilter filter = new IntentFilter();
//        filter.addAction();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mBroadcastReceiver, filter);
    }


    private class ServiceHandler extends Handler {
        public ServiceHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Intent intent = (Intent) msg.obj;

            switch (msg.what) {
                case COMMAND_CONNECT:
                    mConfigId = intent.getStringExtra("config_id");
                    mConfigType = intent.getStringExtra("config_type");
                    int mode = intent.getIntExtra("connect_mod",MODE_CONNECT);
                    if (mode == MODE_CONNECT){
                        mStopService = false;
                    }
                    connect(mConfigId, mConfigType);
                    break;
                case COMMAND_DISCONNECT:
                    boolean byUser = intent.getBooleanExtra("by_user", false);
                    disconnect(byUser);
                    break;
                case COMMAND_RECONNECT:
                    retry();
                    break;
            }
        }
    }

     class RetryRunnable implements Runnable {
        private String configId;
        private String configType;

        RetryRunnable(String configId, String configType) {
            this.configId = configId;
            this.configType = configType;
        }

        @Override
        public void run() {
            if (mStatus == Status.DISCONNECTING || mStatus == Status.DISCONNECTED ){
                return;
            }
            connect(getApplicationContext(), configId, configType,MODE_RECONNECT);
        }
    }
    private RetryRunnable mRetryRunnable;
    @SuppressLint("DefaultLocale")
    private void retry() {

        if (TextUtils.isEmpty(mConfigId)) {
            forceDisconnect();
            return;
        }
        if (mStatus == Status.DISCONNECTING || mStatus == Status.DISCONNECTED || mStatus == Status.RETRYING){
            return;
        }
        if (mStopService) {
            return;
        }

        Logger.logMessage(new LogItem("Reconnecting ..."));
        sendStatusChangedSignal(Status.RETRYING);
        if (retryCount % 3 == 0) {
            retryWaitTime += 2;
        }
        retryCount++;
        terminate();
        if (mRetryRunnable != null){
            mUiThreadHandler.removeCallbacks(mRetryRunnable);
        }
        mRetryRunnable = new RetryRunnable(mConfigId,mConfigType);
        if (retryWaitTime > 0) {
            Logger.logMessage(new LogItem(String.format("[VPNService] waiting for %d seconds before retying...", retryWaitTime)));
        }
        mUiThreadHandler.postDelayed(mRetryRunnable ,retryWaitTime * 1000L);


    }


    public void forceDisconnect() {
        disconnect(true);
    }

    public void disconnect(boolean byUser) {
        if (mStatus == Status.NETWORK_ERROR && !byUser) {
            return;
        }
        sendStatusChangedSignal(Status.DISCONNECTING);
        mStopService = true;
//        mStatus = Status.DISCONNECTED;
        retryCount = 0;
        retryWaitTime = 0;
        if (mRetryRunnable != null){
            mUiThreadHandler.removeCallbacks(mRetryRunnable);
        }
        terminate();
        Logger.logStyledMessage("YOU ARE DISCONNECTED", "red", true);
        sendStatusChangedSignal(Status.DISCONNECTED);
        stopForeground(true);
        stopSelf();

    }

    private void terminate() {


        if (mCurrentConfig != null) {
            mCurrentConfig.cancel();
        }


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int commandCode = 0;
        if (intent != null) {
            commandCode = intent.getIntExtra("command", COMMAND_DISCONNECT);
            Message msg = mServiceHandler.obtainMessage(commandCode);
            msg.obj = intent;
            mServiceHandler.sendMessage(msg);
            if (commandCode == COMMAND_CONNECT) {
                makeForeground();
            }
        }
        return commandCode == COMMAND_DISCONNECT ? START_NOT_STICKY : START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && Objects.equals(intent.getAction(),SERVICE_INTERFACE)){
            return super.onBind(intent);
        }
        return mBinder;

    }

    @SuppressLint("DefaultLocale")
    private  void connect(String configId, String configType) {

        if (mStatus == Status.NETWORK_ERROR || !NetworkUtils.isConnected(getApplicationContext())) {
            mStatus = Status.NETWORK_ERROR;
            return;
        }

        if (mConfigType == null || mConfigId == null){
            return;
        }
        if (mStopService){
            return;
        }

        synchronized (mConnectLock) {
            if (mStopService){
                return;
            }
            if (mStatus == Status.CONNECTED){
                return;
            }
            sendStatusChangedSignal(Status.CONNECTING);

            try {
                Logger.logMessage(new LogItem("[VPNService] start connecting..."));
                mCurrentConfig = ConfigsUtil.loadConfig(getApplicationContext(), configId, configType);
//                GoTun2SocksConfig config = new GoTun2SocksConfig("squid_conf", "sqid_id",
//                        new HttpProxy("127.0.0.1", 1080), "s3.goolha.tk", 2232,
//                        "mostafa", "mosi.1371", false, null, 7600);
//                mCurrentConfig = config;

                Proxy globalProxy = loadProxy();
                if (globalProxy != null) {
                    mCurrentConfig.setProxy(globalProxy);
                }
            } catch (Exception e) {
                Logger.logError(getString(R.string.can_not_read_config));
                forceDisconnect();
                return;
            }
            if (mCurrentConfig == null) {
                forceDisconnect();
                return;
            }
            mCurrentConfig.setVpnService(this);
            try {
                mCurrentConfig.connect();
                int mainSocketDescriptor = mCurrentConfig.getMainSocketDescriptor();
                if (mainSocketDescriptor != -1) {
                    protect(mCurrentConfig.getMainSocketDescriptor());
                }
            } catch (ConfigException e) {
                if (mStatus == Status.NETWORK_ERROR || !NetworkUtils.isConnected(getApplicationContext())) {
                    return;
                }
                if (e.getCause() instanceof RemoteConnectionException) {
                    if (e.getCause().getCause() instanceof AuthFailedException) {
                        Logger.logStyledMessage("SSH server auhtentication failed", "#B83127", true);
                        AlertUtil.showToast(this, R.string.authentication_failed_message, Toast.LENGTH_LONG, AlertUtil.Type.ERROR);
                        forceDisconnect();
                        return;
                    }
                }
                String message = null;
                Throwable t = e;
                while (message == null) {
                    if (t != null) {
                        message = t.getMessage();
                    }
                    if (message == null) {
                        t = e.getCause();
                    }
                }
                Logger.logMessage(new LogItem(String.format("Error in connection : " + message)));

                retry();

            }
        }


    }

    public void onConnect() {
        retryCount = 0;
        retryWaitTime = 0;
//        mStatus = Status.CONNECTED;
        sendStatusChangedSignal(Status.CONNECTED);
        Logger.logStyledMessage("YOU ARE CONNECTED", "green", true);
        playConnectSound();
    }

    public void onReconnecting() {
//        mStatus = Status.CONNECTING;
        sendStatusChangedSignal(Status.RETRYING);
    }

    @Override
    public void onNetworkDisconnected() {
        Logger.logMessage(new LogItem("Network is disconnected. Wating for connecting again"));
//        mStatus = Status.NETWORK_ERROR;
        if (mStatus == Status.DISCONNECTED || mStatus == Status.DISCONNECTING){
            return;
        }
        sendStatusChangedSignal(Status.NETWORK_ERROR);
        retryCount = 0;
        retryWaitTime = 0;
        if (mCurrentConfig != null) {
            mCurrentConfig.cancel();
        }
    }

    @Override
    public void onNetworkConnected(boolean changed) {
        if (changed && mStatus == Status.CONNECTED) {
            Logger.logMessage(new LogItem("Network changed"));
            retry();
        } else if (mStatus == Status.NETWORK_ERROR) {
            Logger.logMessage(new LogItem("Network connected"));
            retry();
        }


    }

    synchronized void registerDeviceStateReceiver(NetworkStateReceiver newDeviceStateReceiver) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        // Fetch initial network state
        newDeviceStateReceiver.networkStateChange(getApplicationContext());

        registerReceiver(newDeviceStateReceiver, filter);
//        VpnStatus.addByteCountListener(newDeviceStateReceiver);
    }

    synchronized void unregisterDeviceStateReceiver(NetworkStateReceiver deviceStateReceiver) {
        if (mNetworkStateReceiver != null)
            try {
//                VpnStatus.removeByteCountListener(deviceStateReceiver);
                unregisterReceiver(deviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                // I don't know why  this happens:
                // java.lang.IllegalArgumentException: Receiver not registered: de.blinkt.openvpn.NetworkSateReceiver@41a61a10
                // Ignore for now ...
                iae.printStackTrace();
            }
    }

    public void onExpireDateReceived(String expireDate){
        Intent intent = new Intent(MainActivity.ACTION_EXPIRE_DATE);
        intent.putExtra("expire_date",expireDate);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
    public Builder getBuilder() {
        Builder builder = new Builder();
        return builder;
    }

    private Proxy loadProxy() {
        int proxyType = PrefsUtil.getGlobalProxyType(getApplicationContext());
        if (proxyType == ProxyType.TYPE_NONE) {
            return null;
        }
        if (proxyType == ProxyType.TYPE_HTTP) {
            String proxyJson = PrefsUtil.loadGlobalProxy(getApplicationContext());
            if (!TextUtils.isEmpty(proxyJson)) {
                return new Gson().fromJson(proxyJson, HttpProxy.class);
            }
        }
        if (proxyType == ProxyType.TYPE_SSH) {
            String proxyJson = PrefsUtil.loadGlobalProxy(getApplicationContext());
            if (!TextUtils.isEmpty(proxyJson)) {
                return new Gson().fromJson(proxyJson, SSHProxy.class);
            }
        }
        return null;
    }

    private void sendStatusChangedSignal(Status status) {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());
        Intent intent = new Intent();
        String notificationText = getString(R.string.connecting_);
        int notificationIcon = R.drawable.ic_cloud_outline;
        switch (status) {
            case CONNECTED:
                intent.setAction(ACTION_CONNECTED);
                notificationText = getString(R.string.connected);
                notificationIcon = R.drawable.ic_cloud_filled;
                break;
            case CONNECTING:
                intent.setAction(ACTION_CONNECTING);
                notificationText = getString(R.string.connecting_);
                if (mStatus == Status.CONNECTED) {
                    playDisconnectSound();
                }
                break;
            case RETRYING:
                intent.setAction(ACTION_RETRYING);
                notificationText = getString(R.string.retrying);
                break;
            case DISCONNECTING:
                intent.setAction(ACTION_DISCONNECTING);
                notificationText = getString(R.string.disconncting);
                break;
            case DISCONNECTED:
                intent.setAction(ACTION_DISCONNECTED);
                break;
            case NETWORK_ERROR:
                intent.setAction(ACTION_CONNECTING);
                notificationText = getString(R.string.waiting_for_network);
                notificationIcon = R.drawable.ic_cloud_disconnected;
                if (mStatus == Status.CONNECTED) {
                    playDisconnectSound();
                }
                break;
        }
        if (mStatus != Status.DISCONNECTED) {
            updateNotificationInfo(notificationText, notificationIcon);
        }
        manager.sendBroadcast(intent);
        mStatus = status;
    }

    private void playDisconnectSound() {
        if (PrefsUtil.isConnectionSoundEnabled(getApplicationContext())) {
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), Util.getRawUri(getApplicationContext(), R.raw.disconnect));
            ringtone.play();
        }
    }
    private void playConnectSound() {
        if (PrefsUtil.isConnectionSoundEnabled(getApplicationContext())) {
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), Util.getRawUri(getApplicationContext(), R.raw.connect));
            ringtone.play();
        }
    }

    public PendingIntent getMainIntent() {
        int flag = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                PendingIntent.FLAG_UPDATE_CURRENT;
        Intent intent = new Intent(getApplicationContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                getPackageName().hashCode(),intent,flag);

        return pi;
    }


    public static String getRemoteAddr(byte[] data) {
        IPV4Header header = IPV4Header.fromHeaderByte(data);
        return header.getDestAddressName();
    }


    private void updateNotificationInfo(String text, int icon) {
        Notification notification = createNotification(icon, text);
        startForeground(NOTIFICATION_ID, notification);
    }

    private void makeForeground() {

        Notification notification = createNotification(R.drawable.ic_cloud_outline, getString(R.string.connecting_));
        startForeground(NOTIFICATION_ID, notification);

    }

    private Notification createNotification(int icon, String contentText) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                NOTIFICATION_CHANNEL_BG_ID);

        builder.addAction(createDisconnectAction());
        builder.addAction(createReconnectAction());
        builder.setSmallIcon(icon);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null)
                .setVibrate(null)
                .setContentText(contentText)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(getMainIntent());
        return builder.build();
    }
    private static final int ACTION_DISCONNECT_ID = 2300;
    private static final int ACTION_RECONNECT_ID = 2301;
    private NotificationCompat.Action createDisconnectAction(){
        Intent disconnectIntent = new Intent(getApplicationContext(), MyVpnService.class);
        disconnectIntent.putExtra("command", COMMAND_DISCONNECT);
        disconnectIntent.putExtra("by_user", true);
        PendingIntent disconnectPi = PendingIntent.getService(getApplicationContext(),
                ACTION_DISCONNECT_ID,disconnectIntent,getPIFlag());
        return new NotificationCompat.Action(0,getString(R.string.disconnect),disconnectPi);
    }
    private NotificationCompat.Action createReconnectAction(){
        Intent reconnectIntent = new Intent(getApplicationContext(), MyVpnService.class);
        reconnectIntent.putExtra("command", COMMAND_RECONNECT);
        PendingIntent disconnectPi = PendingIntent.getService(getApplicationContext(),
                ACTION_RECONNECT_ID,reconnectIntent,getPIFlag());
        return new NotificationCompat.Action(0,getString(R.string.reconnect),disconnectPi);
    }
    private int getPIFlag(){
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    @Override
    public void onDestroy() {
        unregisterDeviceStateReceiver(mNetworkStateReceiver);
        terminate();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        forceDisconnect();
    }

    public static class VpnBinder extends Binder {
        private MyVpnService vpnService;

        public VpnBinder(MyVpnService vpnService) {
            this.vpnService = vpnService;
        }

        public MyVpnService getVpnService() {
            return vpnService;
        }
    }

    public static void connect(Context context, String configId, String configType,int connectionMode) {

        Intent startIntent = new Intent(context, MyVpnService.class);
        startIntent.putExtra("command", COMMAND_CONNECT);
        startIntent.putExtra("config_id", configId);
        startIntent.putExtra("config_type", configType);
        startIntent.putExtra("connect_mod", connectionMode);
        ContextCompat.startForegroundService(context, startIntent);
    }

    public static void disconnect(Context context, boolean byUser) {
        Intent disconnectIntent = new Intent(context, MyVpnService.class);
        disconnectIntent.putExtra("command", COMMAND_DISCONNECT);
        disconnectIntent.putExtra("by_user", byUser);
        ContextCompat.startForegroundService(context, disconnectIntent);
    }

    public static void reconnect(Context context) {
        Intent startIntent = new Intent(context, MyVpnService.class);
        startIntent.putExtra("command", COMMAND_RECONNECT);
        ContextCompat.startForegroundService(context, startIntent);
    }
}
