package ir.smartdevelopers.smarttunnel;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.Network;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.activities.MainActivity;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;
import ir.smartdevelopers.smarttunnel.ui.models.ProxyType;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHProxy;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class MyVpnService extends VpnService {

    public static final int COMMAND_CONNECT = 1;
    public static final int COMMAND_DISCONNECT= 2;
    public static final int COMMAND_RECONNECT= 3;
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    public static final String ACTION_CONNECTED = "connected";
    public static final String ACTION_DISCONNECTED = "disconnected";
    public static final String ACTION_CONNECTING = "connecting";
    private static final int NOTIFICATION_ID = 50;
    private ParcelFileDescriptor vpnInterface = null;
    private static final String PRIVATE_VLAN4_CLIENT = "10.0.0.1";
    private static final String PRIVATE_VLAN4_ROUTER = "10.0.0.2";

    private static final String PRIVATE_VLAN6_CLIENT = "fc00::1";
    private static final String PRIVATE_VLAN6_ROUTER = "fc00::2";

    private static final String PRIVATE_NETMASK = "255.255.255.252";

    private static final int PRIVATE_MTU = 1500;
    private  boolean mStopService;

    private ConnectivityManager connectivityManager;
    private HandlerThread mServiceThread;
    private Handler mUiThreadHandler;
    private ServiceHandler mServiceHandler;
    private LocalReader mLocalReaderThread;
    private Config mCurrentConfig;
    private String mConfigId;
    private String mConfigType ;
    private VpnBinder mBinder;
    /** Connection status*/
    public Status mStatus = Status.DISCONNECTED;

    private BroadcastReceiver mBroadcastReceiver;

    public enum Status{
        CONNECTED,CONNECTING,DISCONNECTED,NETWORK_ERROR
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
    }

    private void initBroadcast() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };
        IntentFilter filter = new IntentFilter();
//        filter.addAction();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mBroadcastReceiver,filter);
    }


    private class ServiceHandler extends Handler{
        public ServiceHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Intent intent = (Intent) msg.obj;
            switch (msg.what){
                case COMMAND_CONNECT:
                    mConfigId = intent.getStringExtra("config_id");
                    mConfigType = intent.getStringExtra("config_type");
                    connect(mConfigId,mConfigType);
                    break;
                case COMMAND_DISCONNECT:
                    disconnect();
                    break;
                case COMMAND_RECONNECT:
                    retry();
                    break;
            }
        }
    }

    private void retry() {

        if (TextUtils.isEmpty(mConfigId)){
            disconnect();
            return;
        }
        if (mStopService){
            return;
        }
        terminate();
        connect(mConfigId,mConfigType);
    }
    private void disconnect() {
        mStopService = true;
        mStatus = Status.DISCONNECTED;
        sendStatusChangedSignal();
       terminate();
        stopForeground(true);
        stopSelf();

    }

    private void terminate() {
        if (mLocalReaderThread != null){
            mLocalReaderThread.interrupt();
            mLocalReaderThread = null;
        }

        if (mCurrentConfig != null){
            mCurrentConfig.cancel();
        }
        if (vpnInterface != null){
            try {
                vpnInterface.close();
            } catch (IOException ignore) {

            }
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int commandCode = intent.getIntExtra("command",COMMAND_DISCONNECT);
        Message msg = mServiceHandler.obtainMessage(commandCode);
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        if (commandCode == COMMAND_CONNECT){
            makeForeground();
        }
        return commandCode == COMMAND_DISCONNECT ? START_NOT_STICKY : START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void connect(String configId, String configType){
        mStatus = Status.CONNECTING;
        sendStatusChangedSignal();
        try {
            mCurrentConfig = ConfigsUtil.loadConfig(getApplicationContext(),configId,configType);
            Proxy globalProxy = loadProxy();
            if (globalProxy != null){
                mCurrentConfig.setProxy(globalProxy);
            }
        }catch (IOException e){
            Logger.logError(getString(R.string.can_not_read_config));
            disconnect();
        }
        if (mCurrentConfig == null ){
            disconnect();
            return;
        }
        try {
            mCurrentConfig.connect();
        } catch (ConfigException e) {
            if (mCurrentConfig.isCanceled()){
                disconnect();

            }else {
                retry();
            }
            return;
        }
        Socket socket = mCurrentConfig.getMainSocket();
        if (socket!=null){
            protect(mCurrentConfig.getMainSocket());
        }
        try {

            VpnService.Builder builder=new VpnService.Builder();
            builder.setSession("SmartTunnel").setMtu(PRIVATE_MTU);
            builder.addAddress(PRIVATE_VLAN4_CLIENT, 24);
//            builder.addAddress(PRIVATE_VLAN6_CLIENT, 64);

            PackageManager packageManager = getPackageManager();
            // allow selected apps to use vpn
            Set<String> selectedApps = PrefsUtil.getSelectedApps(getApplicationContext());
            Set<String> forbiddenApps = PrefsUtil.getForbiddenApps(getApplicationContext());

            if (PrefsUtil.isAllowSelectedAppsEnabled(getApplicationContext())){
                for (String app : selectedApps){
                    if (forbiddenApps.contains(app)){
                        continue;
                    }
                    try {
                        packageManager.getPackageInfo(app,0);
                        builder.addAllowedApplication(app);
                    } catch (PackageManager.NameNotFoundException ignore) {}
                }

            }else {
                Set<String> allDisallowedApps = new HashSet<>(selectedApps);
                allDisallowedApps.addAll(forbiddenApps);
                for (String app : allDisallowedApps){
                    try {
                        packageManager.getPackageInfo(app,0);
                        builder.addDisallowedApplication(app);
                    } catch (PackageManager.NameNotFoundException ignore) {}
                }
            }
//            builder.addAllowedApplication("ir.smartdevelopers.tcptest");
//            builder.addAllowedApplication("com.android.chrome");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    builder.setUnderlyingNetworks(new Network[] {activeNetwork});
                }
            }
            String DNS1 = PrefsUtil.getDNS1(getApplicationContext());
            if (!TextUtils.isEmpty(DNS1)){
                builder.addDnsServer(DNS1);
            }
            String DNS2 = PrefsUtil.getDNS1(getApplicationContext());
            if (!TextUtils.isEmpty(DNS2)){
                builder.addDnsServer(DNS2);
            }
//            builder.addDnsServer("8.8.8.8");
            builder.addRoute("0.0.0.0", 0);
            builder.addRoute("::", 0);
//            builder.excludeRoute(new IpPrefix(InetAddress.getByAddress(new byte[]{}),32));
            builder.setConfigureIntent(getMainIntent());
            vpnInterface = builder.establish();


            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            OnPacketFromServerListener packetListener = new OnPacketFromServerListener(out);
            mCurrentConfig.setOnPacketFromServerListener(packetListener);
            mLocalReaderThread = new LocalReader(in,mCurrentConfig,this);
            mLocalReaderThread.start();

            mStatus = Status.CONNECTED;
            sendStatusChangedSignal();

        }catch (Exception e){
            disconnect();
            //todo : handel this
            throw new RuntimeException(e);
        }

    }

    private Proxy loadProxy() {
        int proxyType = PrefsUtil.getGlobalProxyType(getApplicationContext());
        if (proxyType == ProxyType.TYPE_NONE){
            return null;
        }
        if (proxyType == ProxyType.TYPE_HTTP){
            String proxyJson = PrefsUtil.loadGlobalProxy(getApplicationContext());
            if (!TextUtils.isEmpty(proxyJson)){
                return new Gson().fromJson(proxyJson,HttpProxy.class);
            }
        }
        if (proxyType == ProxyType.TYPE_SSH){
            String proxyJson = PrefsUtil.loadGlobalProxy(getApplicationContext());
            if (!TextUtils.isEmpty(proxyJson)){
                return new Gson().fromJson(proxyJson,SSHProxy.class);
            }
        }
        return null;
    }

    private void sendStatusChangedSignal() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());
        Intent intent = new Intent();
        String notificationText = getString(R.string.connecting_);
        int notificationIcon = R.drawable.ic_cloud_outline;
        switch (mStatus){
            case CONNECTED:
                intent.setAction(ACTION_CONNECTED);
                notificationText = getString(R.string.connected);
                notificationIcon = R.drawable.ic_cloud_filled;
                break;
            case CONNECTING:
                intent.setAction(ACTION_CONNECTING);
                notificationText = getString(R.string.connecting_);
                break;
            case DISCONNECTED:
                intent.setAction(ACTION_DISCONNECTED);
                break;
            case NETWORK_ERROR:
                intent.setAction(ACTION_CONNECTING);
                notificationText = getString(R.string.waiting_for_network);
                notificationIcon = R.drawable.ic_cloud_disconnected;
                break;
        }
        if (mStatus != Status.DISCONNECTED){
            updateNotificationInfo(notificationText,notificationIcon);
        }
        manager.sendBroadcast(intent);
    }



    private PendingIntent getMainIntent() {
        int flag = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                getPackageName().hashCode(),
                new Intent(getApplicationContext(), MainActivity.class),
                flag);

        return pi;
    }

    private static class OnPacketFromServerListener implements Config.OnPacketFromServerListener{

        private FileOutputStream localOut;

        private OnPacketFromServerListener(FileOutputStream localOut) {
            this.localOut = localOut;
        }

        @Override
        public void onPacketFromServer(byte[] packet) {
            try {
                localOut.write(packet);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private static class LocalReader extends Thread{
        private final FileInputStream mLocalInput;
        private final Config mConfig;
        private final MyVpnService mMyVpnService;
        final long IDLE_TIME = 50;
        final byte[] packet = new byte[Packet.MAX_SIZE];
        private LocalReader(FileInputStream localInput, Config config,MyVpnService service) {
            mLocalInput = localInput;
            mConfig = config;
            mMyVpnService = service;
        }

        @Override
        public void run() {
            int len=0;
            try{

                while (true){
                    if (mConfig.isCanceled()){
                        return;
                    }
                    boolean idle = true;
                    ByteUtil.clear(packet);
                    len=mLocalInput.read(packet);
                    if (len >0){
                        mConfig.sendPacketToRemoteServer(Arrays.copyOfRange(packet,0,len));
                        idle=false;
                    }

                    if (idle){
                        Thread.sleep(IDLE_TIME);
                    }
                }
            } catch (IOException | InterruptedException e) {
                mMyVpnService.retry();
            }
        }


    }

    public static String getRemoteAddr(byte[] data){
        IPV4Header header = IPV4Header.fromHeaderByte(data);
        return header.getDestAddressName();
    }


    private void updateNotificationInfo(String text,int icon) {
        Notification notification = createNotification(icon,text);
        startForeground(NOTIFICATION_ID,notification);
    }
    private void makeForeground(){
        NotificationChannelCompat.Builder channelBuilder = new NotificationChannelCompat
                .Builder("my_vpn_channel", NotificationManagerCompat.IMPORTANCE_DEFAULT);
        NotificationChannelCompat channelCompat = channelBuilder.setName("my vpn notification")
                .setDescription("my vpn notification")
                .build();
        NotificationManagerCompat managerCompat= NotificationManagerCompat.from(getApplicationContext());
        managerCompat.createNotificationChannel(channelCompat);
        Notification notification = createNotification(R.drawable.ic_cloud_outline,getString(R.string.connecting_));
        startForeground(NOTIFICATION_ID,notification);

    }
    private Notification createNotification(int icon,String contentText){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                "my_vpn_channel");
        builder.setSmallIcon(icon);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null)
                .setVibrate(null)
                .setContentText(contentText)
                .setContentTitle(getString(R.string.app_name));
        return builder.build();
    }

    @Override
    public void onDestroy() {
        terminate();
        super.onDestroy();
    }


    public static class VpnBinder extends Binder{
        private MyVpnService vpnService;

        public VpnBinder(MyVpnService vpnService) {
            this.vpnService = vpnService;
        }

        public MyVpnService getVpnService() {
            return vpnService;
        }
    }

    public static void connect(Context context,String configId,String configType){

        Intent startIntent = new Intent(context,MyVpnService.class);
        startIntent.putExtra("command",COMMAND_CONNECT);
        startIntent.putExtra("config_id",configId);
        startIntent.putExtra("config_type",configType);
        ContextCompat.startForegroundService(context,startIntent);
    }
    public static void disconnect(Context context){
        Intent startIntent = new Intent(context,MyVpnService.class);
        startIntent.putExtra("command",COMMAND_DISCONNECT);
        ContextCompat.startForegroundService(context,startIntent);
    }
    public static void reconnect(Context context){
        Intent startIntent = new Intent(context,MyVpnService.class);
        startIntent.putExtra("command",COMMAND_RECONNECT);
        ContextCompat.startForegroundService(context,startIntent);
    }
}
