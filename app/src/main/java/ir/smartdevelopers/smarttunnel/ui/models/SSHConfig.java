package ir.smartdevelopers.smarttunnel.ui.models;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import com.jcraft.jsch.HostKeyRepository;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ir.smartdevelopers.smarttunnel.BuildConfig;
import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.channels.RemoteConnection;
import ir.smartdevelopers.smarttunnel.channels.Ssh2RemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;
import ir.smartdevelopers.tun2socks.DNSForwarder;
import ir.smartdevelopers.tun2socks.DynamicDNSForwarder;

public class SSHConfig extends Config implements JschRemoteConnection.OnDisconnectListener{
    /**
     * This is SSH config type
     */
    public final static String CONFIG_TYPE = "SSH";
    public static int CONNECTION_TYPE_DIRECT = 1001;
    public static int CONNECTION_TYPE_WEBSOCKET = 1002;
    public static int CONNECTION_TYPE_SSH_PROXY = 1003;
    public static int CONNECTION_TYPE_SSL_TLS = 1004;
    public static int DEFAULT_UDPGW_PORT = 7300;
    public static int MODE_MAIN_CONNECTION = 300;
    public static int MODE_PROXY = 301;
    private static final int PRIVATE_MTU = 1500;
    private static final String PRIVATE_VLAN4_CLIENT = "10.0.0.1";
    private static final String PRIVATE_VLAN4_ROUTER = "0.0.0.0";
    private static final String PRIVATE_VLAN6_CLIENT = "fc00::1";
    private static final String PRIVATE_VLAN6_ROUTER = "::";
    private static final String PRIVATE_NETMASK = "255.255.255.0";
    private static final String LOCAL_SOCKS_ADDRESS = "127.0.0.1";
    private static final int LOCAL_SOCKS_PORT = 1080;
    private static final String LOCAL_DNS_ADDRESS = "127.0.0.1";
    private static final int LOCAL_TCP_DNS_PORT = 5050;
    private static final int LOCAL_UDP_DNS_PORT = 5051;
    /**
     * determine this config is proxy or main connection
     */
    private int mConfigMode;
    private String mServerAddress;
    private int mServerPort;
    private String mUsername;
    private String mPassword;
    private boolean mUsePrivateKey;
    private PrivateKey mPrivateKey;
    private int mUDPGWPort;
    private SSHProxy mJumper;
    private int mConnectionType;
    private String mPayload;
    private String mServerNameIndicator;
    private boolean serverAddressLocked;
    private boolean serverPortLocked;
    private boolean usernameLocked;
    private boolean passwordLocked;
    private boolean privateKeyLocked;
    private boolean connectionModeLocked;
    private boolean preferIPv6;
    private boolean useRemoteSocksServer = true;
    private String mRemoteSocksAddress;
    private int mRemoteSocksPort;
    private transient HostKeyRepository mHostKeyRepo;
    transient RemoteConnection mRemoteConnection;
    private transient boolean mCanceled;
    private transient boolean tun2socksInitialized;
//    private transient Tun2SocksThread mTun2socksThread;
    private transient Thread mTun2socksThread;
    private transient Process mTun2SocksProcess;
    private transient Thread mDnsgwThread;
    private transient Thread mExpireDateCheckThread;

    private SSHConfig(String name, String id, String type) {
        super(name, id, type);
        mHostKeyRepo = new AcceptAllHostRepo();
    }

    public SSHConfig(String name, String id, String type, int configMode, String serverAddress,
                     int serverPort, String username, String password,
                     boolean usePrivateKey, PrivateKey privateKey, int UDPGWPort,
                     SSHProxy jumper, int connectionType, String payload,
                     String serverNameIndicator) {
        super(name, id, type);
        mConfigMode = configMode;
        mServerAddress = serverAddress;
        mServerPort = serverPort;
        mUsername = username;
        mPassword = password;
        mUsePrivateKey = usePrivateKey;
        mPrivateKey = privateKey;
        mUDPGWPort = UDPGWPort;
        mJumper = jumper;
        mConnectionType = connectionType;
        mPayload = payload;
        mServerNameIndicator = serverNameIndicator;
        mHostKeyRepo = new AcceptAllHostRepo();
    }

    @Override
    public void connect() throws ConfigException {
        try {

            PrivateKey privateKey = mPrivateKey;
            if (mUsePrivateKey && mPrivateKey != null) {
                privateKey = null;
            }
            String proxifiedAddress = mServerAddress;
            int proxifiedPort = mServerPort;
            if (getProxy() == null) {
                if (mJumper != null) {
                    connectSSHProxy(mJumper, mServerAddress, mServerPort);
                    proxifiedAddress = mJumper.getAddress();
                    proxifiedPort = mJumper.getPort();
                }
            } else {
                if (getProxy() instanceof SSHProxy) {
                    if (mJumper != null) {
                        // in this case jumper have a server address for its sshconfig
                        // and we must set a local port forwarding to that address
                        // then jumper must connect to getProxy().getAddress() and
                        // getProxy().getPort() instead
                        connectSSHProxy((SSHProxy) getProxy(), mJumper.getSSHConfig().getServerAddress(),
                                mJumper.getSSHConfig().getServerPort());
                        mJumper.getSSHConfig().setServerAddress(getProxy().getAddress());
                        mJumper.getSSHConfig().setServerPort(getProxy().getPort());
                        connectSSHProxy(mJumper, mServerAddress, mServerPort);
                        proxifiedAddress = mJumper.getAddress();
                        proxifiedPort = mJumper.getPort();
                    } else {
                        Logger.logMessage(new LogItem("Connecting to global ssh proxy"));
                        connectSSHProxy((SSHProxy) getProxy(), mServerAddress, mServerPort);
                        proxifiedAddress = getProxy().getAddress();
                        proxifiedPort = getProxy().getPort();
                    }
                }
            }
            String dns = null;
            String dns1 = PrefsUtil.getDNS1(mVpnService.getApplicationContext());
            if (!TextUtils.isEmpty(dns1)){
                dns = dns1;
            }else {
                String dns2 = PrefsUtil.getDNS2(mVpnService.getApplicationContext());
                if (!TextUtils.isEmpty(dns2)){
                    dns = dns2;
                }
            }
            if (dns == null){
                throw new ConfigException("DNS not set");
            }
            JschRemoteConnection connection = new JschRemoteConnection(proxifiedAddress,proxifiedPort,mUsername,mPassword,
                    isServerAddressLocked(), isServerPortLocked(), isUsernameLocked(),dns,preferIPv6);
//            Ssh2RemoteConnection connection = new Ssh2RemoteConnection(proxifiedAddress,proxifiedPort,mUsername,mPassword,
//                    isServerAddressLocked(), isServerPortLocked(), isUsernameLocked(),dns,preferIPv6);
            connection.setOnDisconnectListener(this);
            connection.setPrivateKey(privateKey);
            mRemoteConnection = connection;
            if (getProxy() instanceof HttpProxy) {
                mRemoteConnection.setProxy(getProxy());
            }
            mRemoteConnection.connect();
            if (mConfigMode == MODE_PROXY) {
                return;
            }
            int socksPort = 0;

            if (useRemoteSocksServer){
                socksPort =mRemoteConnection.startLocalPortForwarding(LOCAL_SOCKS_ADDRESS,LOCAL_SOCKS_PORT,
                        mRemoteSocksAddress,mRemoteSocksPort);

            }else {
                mRemoteConnection.startDynamicForwarder(LOCAL_SOCKS_PORT);
                socksPort = LOCAL_SOCKS_PORT;
            }

            int udpdwPort = mRemoteConnection.startLocalPortForwarding("127.0.0.1",mUDPGWPort,"127.0.0.1",mUDPGWPort);
            Logger.logMessage(new LogItem("Starting DNS forwarding"));
            startDnsGw(dns,LOCAL_SOCKS_ADDRESS,socksPort);
            Logger.logMessage(new LogItem("DNS forwarding started"));
            fetchExpireDate();
            mFileDescriptor = openTun();



            runTun2socks(LOCAL_SOCKS_ADDRESS,LOCAL_SOCKS_PORT);
            mVpnService.onConnect();


        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
    }

    private void fetchExpireDate() {
        try {
            mRemoteConnection.startLocalPortForwarding("127.0.0.1",6161,"127.0.0.1",6161);
            mExpireDateCheckThread = new Thread(()->{
                try(Socket socket = new Socket("127.0.0.1",6161);
                    OutputStream outputStream = socket.getOutputStream();
                    InputStream inputStream = socket.getInputStream()
                    ) {
                    outputStream.write((mUsername+"\n").getBytes());
                    byte[] buff = new byte[256];
                    int len = 0;
                    len = inputStream.read(buff);
                    if (len > 0){
                        String date = new String(buff,0,len).trim();
                        mVpnService.onExpireDateReceived(date);
                    }

                } catch (Exception e) {
                    //ignore
                }finally {
                    try {
                        mRemoteConnection.stopLocalPortForwarding("127.0.0.1",6161);
                    } catch (RemoteConnectionException e) {
                        //ignore
                    }
                }
            });
            mExpireDateCheckThread.start();
        } catch (RemoteConnectionException e) {
            //ignore
        }
    }

    private void startDnsGw(String dns,String socksAddress,int socksPort) throws RemoteConnectionException {

        if (!mRemoteConnection.isPortInUse(LOCAL_TCP_DNS_PORT)){
            mRemoteConnection.startLocalPortForwarding(LOCAL_DNS_ADDRESS,LOCAL_TCP_DNS_PORT,dns,53);
        }
        if (mDnsgwThread != null){
            mDnsgwThread.interrupt();
        }
        mDnsgwThread  = new DnsgwThread(dns, socksPort, socksAddress);
        mDnsgwThread.start();
    }

    @Override
    public void onDisconnected() {
        if (!mCanceled){
            MyVpnService.reconnect(mVpnService);
        }
    }

    private class DnsgwThread extends Thread{
        private DatagramSocket mSocket;
        private String mDnsServer;
        private int socksPort;
        private String socksAddress;

        public DnsgwThread(String dns, int socksPort, String socksAddress) {
            this.socksPort = socksPort;
            this.socksAddress = socksAddress;
            setName("DnsgwThread");
            mDnsServer = dns;
        }

        @Override
        public void run() {
            try {

                mSocket= new DatagramSocket(LOCAL_UDP_DNS_PORT);
                while (!isCanceled() || !mDnsgwThread.isInterrupted()){
                    byte[] buff = new byte[2024];
                    DatagramPacket packet  =  new DatagramPacket(buff,buff.length);
                    mSocket.receive(packet);
                    if (packet.getLength() > 0){
                        new Thread(new DynamicDNSForwarder(packet,mDnsServer,53,mRemoteConnection)).start();
//                        new Thread(new DNSForwarder(packet,mDnsServer,53,socksAddress,socksPort)).start();
                    }
                }
            } catch (IOException e) {
                Logger.logMessage(new LogItem("DNS forwarder stopped"));
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
            mSocket.close();
        }
    }
    private static boolean writeMiniVPNBinary(Context context, File mvpnout) {
        try {
            InputStream mvpn;

            try {
                mvpn = context.getAssets().open("tun2socks.armeabi-v7a");
            } catch (IOException errabi) {
//                Logger.logInfo("Failed getting assets for architecture " + abi);
                return false;
            }


            FileOutputStream fout = new FileOutputStream(mvpnout);

            byte[] buf = new byte[4096];

            int lenread = mvpn.read(buf);
            while (lenread > 0) {
                fout.write(buf, 0, lenread);
                lenread = mvpn.read(buf);
            }
            fout.close();

            if (!mvpnout.setExecutable(true)) {
                Logger.logError("Failed to make OpenVPN executable");
                return false;
            }


            return true;
        } catch (IOException e) {
            Logger.logException(e);
            return false;
        }

    }

    private String getTun2socksPath(){
        String[] abis = Build.SUPPORTED_ABIS;

//        if (!nativeAPI.equals(abis[0])) {
//            Logger.logWarning(String.format("abi mismatcehd %s %s", Arrays.toString(abis), nativeAPI));
//            abis = new String[]{nativeAPI};
//        }
//        for (String abi : abis) {

            File vpnExecutable = new File(mVpnService.getCacheDir(), "c_" + "tun2socks.armeabi-v7a");
            if ((vpnExecutable.exists() && vpnExecutable.canExecute()) || writeMiniVPNBinary(mVpnService.getApplicationContext(),
                    vpnExecutable)) {
                return vpnExecutable.getPath();
            }
//        }
        return null;
    }
    private void runTun2socks(String socksAddress,int socksPort) {

        final String TUN2SOCKS = "libtun2socks.so";
        String logLevel = BuildConfig.DEBUG ? "5" : "0";
        File soFile=new File(mVpnService.getApplicationContext().getApplicationInfo().nativeLibraryDir,TUN2SOCKS);
        String soPath = soFile.getAbsolutePath();
//        String soPath = getTun2socksPath();
        if (soPath == null){
            Logger.logError("soPath is null");
            return;
        }
        String[] cmd = Arrays.asList(
                soPath,
                "--netif-ipaddr", PRIVATE_VLAN4_ROUTER,
                "--netif-netmask", PRIVATE_NETMASK,
                "--socks-server-addr", String.format(Locale.ENGLISH,"%s:%d",socksAddress,socksPort),
                "--tunmtu", String.valueOf(PRIVATE_MTU),
                "--netif-ip6addr", PRIVATE_VLAN6_CLIENT,
                "--loglevel", logLevel,
                "--logger", "stdout",
                "--sock-path", "sock_path",
                "--fake-proc",
                "--dnsgw",LOCAL_DNS_ADDRESS+":"+LOCAL_UDP_DNS_PORT,
//                "--tunfd", String.valueOf(mFileDescriptor.getFd())
                "--udpgw-remote-server-addr", "127.0.0.1:"+mUDPGWPort

        ).toArray(new String[0]);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.directory(mVpnService.getApplicationContext().getFilesDir());
            processBuilder.redirectErrorStream(true);
            mTun2SocksProcess = processBuilder.start();
            InputStream in = mTun2SocksProcess.getInputStream();

            mTun2socksThread = new Thread(()->{
                try {
                    Logger.logDebug("Tun2Socks started");
                    mTun2SocksProcess.waitFor();
                    Logger.logDebug("Tun2Socks stopped");

                    try {
                        if (in.available() > 0){
                            byte[] buff=new byte[1024*4];
                            int len = in.read(buff);
                            if (len > 0){
                                System.out.println(new String(buff,0,len));
                            }
                        }
                    } catch (IOException ex) {
                        //ignore
                    }
//                    if (!isCanceled()){
//                        Logger.logDebug("Not canceled so Tun2Socks restarting");
//                        runTun2socks(socksAddress,socksPort);
//                    }
                } catch (InterruptedException e) {

                    if (!isCanceled()){
                        Logger.logDebug("Tun2Socks restarting");
                        runTun2socks(socksAddress,socksPort);
                    }
                }
            });
            mTun2socksThread.start();

            sendFd();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendFd() {
        FileDescriptor fd = mFileDescriptor.getFileDescriptor();
        String path = new File(mVpnService.getApplicationContext().getFilesDir(), "sock_path").getAbsolutePath();

        new Thread(()->{
            int tries = 0;
            while (true){
                try {
                    Thread.sleep(50L * tries);
                    LocalSocket localSocket = new LocalSocket();
                    localSocket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
                    localSocket.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                    localSocket.getOutputStream().write(42);
                    break;

                }catch (Exception e){
                    if (tries > 5){
                        break;
                    }
                    tries ++;
                }
            }
        }).start();
    }



    /**
     * we connect to proxy ssh server first, then we set local port forwarding
     * to destAddress and destPort
     */
    private void connectSSHProxy(SSHProxy proxy, String destAddress, int destPort) throws ConfigException {
        proxy.getSSHConfig().setVpnService(mVpnService);
        proxy.getSSHConfig().connect();
        RemoteConnection connection = proxy.getSSHConfig().mRemoteConnection;
        int localPort;
        try {
            localPort = connection.startLocalPortForwarding(proxy.getAddress(), proxy.getPort(), destAddress, destPort);
        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
        proxy.setPort(localPort);
    }

    public ParcelFileDescriptor openTun(){
        VpnService.Builder builder = mVpnService.getBuilder();

        builder.addAddress(PRIVATE_VLAN4_CLIENT,24);
        builder.addRoute(PRIVATE_VLAN4_ROUTER,0);
//        builder.addAddress(PRIVATE_VLAN6_CLIENT,64);
//        builder.addRoute(PRIVATE_VLAN6_ROUTER,0);

        builder.setSession("SmartTunnel").setMtu(PRIVATE_MTU);

        PackageManager packageManager = mVpnService.getPackageManager();
        // allow selected apps to use vpn
        Set<String> selectedApps = PrefsUtil.getSelectedApps(mVpnService.getApplicationContext());
        Set<String> forbiddenApps = PrefsUtil.getForbiddenApps(mVpnService.getApplicationContext());

        if (PrefsUtil.isAllowSelectedAppsEnabled(mVpnService.getApplicationContext())) {
            if (selectedApps.isEmpty()){
                for (String app : forbiddenApps) {
                    try {
                        packageManager.getPackageInfo(app, 0);
                        builder.addDisallowedApplication(app);
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                }
            }else {
                for (String app : selectedApps) {
                    if (forbiddenApps.contains(app)) {
                        continue;
                    }
                    try {
                        packageManager.getPackageInfo(app, 0);
                        builder.addAllowedApplication(app);
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                }
            }

        } else {
            Set<String> allDisallowedApps = new HashSet<>(selectedApps);
            allDisallowedApps.addAll(forbiddenApps);
            for (String app : allDisallowedApps) {
                try {
                    packageManager.getPackageInfo(app, 0);
                    builder.addDisallowedApplication(app);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
        }
        if (PrefsUtil.isAllowSelectedAppsEnabled(mVpnService.getApplicationContext())){
            // show selected apps as allowed apps in log
            Logger.logMessage(new LogItem("Allowed apps : "+ Arrays.toString(selectedApps.toArray())));
        }else {
            // show selected apps as disallowed apps in log
            Logger.logMessage(new LogItem("Disallowed apps : "+ Arrays.toString(selectedApps.toArray())));

        }

//            builder.addAllowedApplication("ir.smartdevelopers.tcptest")
//            builder.addAllowedApplication("com.android.chrome");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // VPN always uses the default network
            builder.setUnderlyingNetworks(null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Setting this false, will cause the VPN to inherit the underlying network metered
            // value
            builder.setMetered(false);
        }

        String DNS1 = PrefsUtil.getDNS1(mVpnService.getApplicationContext());
        if (!TextUtils.isEmpty(DNS1)) {
            builder.addDnsServer(DNS1);
        }
        String DNS2 = PrefsUtil.getDNS1(mVpnService.getApplicationContext());
        if (!TextUtils.isEmpty(DNS2)) {
            builder.addDnsServer(DNS2);
        }
//        builder.addDnsServer("8.8.8.8");

        builder.setConfigureIntent(mVpnService.getMainIntent());
        try {
            //Debug.stopMethodTracing();
            ParcelFileDescriptor tun = builder.establish();
            Logger.logMessage(new LogItem("TUN established"));
            if (tun == null)
                throw new NullPointerException("Android establish() method returned null (Really broken network configuration?)");
            return tun;
        } catch (Exception e) {
            Logger.logError(mVpnService.getString(R.string.tun_open_error));
            Logger.logError(mVpnService.getString(R.string.error) + e.getLocalizedMessage());
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor getFileDescriptor() {

        return mFileDescriptor;
    }

    @Override
    public int getMainSocketDescriptor() {

        if (mRemoteConnection == null){
            return -1;
        }
        if (getProxy() instanceof SSHProxy){
            return ((SSHProxy) getProxy()).getSSHConfig().getMainSocketDescriptor();
        } else if (mJumper != null) {
            return mJumper.getSSHConfig().getMainSocketDescriptor();
        }
        return mRemoteConnection.getMainSocketDescriptor();
    }

    @Override
    public void retry() {
        if (mVpnService != null){
            MyVpnService.reconnect(mVpnService.getApplicationContext());
        }
    }

    @Override
    public void cancel() {
        mCanceled = true;
        if (mTun2SocksProcess != null){
            mTun2SocksProcess.destroy();
        }
        try {
            if (mTun2socksThread != null){
                mTun2socksThread.join();
            }
        } catch (InterruptedException e) {
            //ignore
        }
        if (mFileDescriptor != null){
            try {
                mFileDescriptor.close();
                mFileDescriptor = null;
            } catch (IOException e) {
                // ignore
            }
        }
        if (mRemoteConnection != null) {

            mRemoteConnection.disconnect();
        }
        if (mJumper != null) {
            mJumper.getSSHConfig().cancel();
        }
        if (getProxy() instanceof SSHProxy){
            ((SSHProxy) getProxy()).getSSHConfig().cancel();
        }
        if (mDnsgwThread != null){
            mDnsgwThread.interrupt();
            mDnsgwThread = null;
        }
        if (mExpireDateCheckThread != null && !mExpireDateCheckThread.isInterrupted()){
            mExpireDateCheckThread.interrupt();
            mExpireDateCheckThread = null;
        }



    }


    public RemoteConnection getRemoteConnection() {
        return mRemoteConnection;
    }

    public String getServerAddress() {
        return mServerAddress;
    }

    public void setServerAddress(String serverAddress) {
        mServerAddress = serverAddress;
    }

    public int getServerPort() {
        return mServerPort;
    }

    public void setServerPort(int serverPort) {
        mServerPort = serverPort;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String password) {
        mPassword = password;
    }

    public boolean isUsePrivateKey() {
        return mUsePrivateKey;
    }

    public void setUsePrivateKey(boolean usePrivateKey) {
        mUsePrivateKey = usePrivateKey;
    }

    public PrivateKey getPrivateKey() {
        return mPrivateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        mPrivateKey = privateKey;
    }

    public int getUDPGWPort() {
        return mUDPGWPort;
    }

    public void setUDPGWPort(int UDPGWPort) {
        mUDPGWPort = UDPGWPort;
    }

    public SSHProxy getJumper() {
        return mJumper;
    }

    public void setJumper(SSHProxy jumper) {
        mJumper = jumper;
    }

    public int getConnectionType() {
        return mConnectionType;
    }

    public void setConnectionType(int connectionType) {
        mConnectionType = connectionType;
    }

    public String getPayload() {
        return mPayload;
    }

    public void setPayload(String payload) {
        mPayload = payload;
    }

    public String getServerNameIndicator() {
        return mServerNameIndicator;
    }

    public void setServerNameIndicator(String serverNameIndicator) {
        mServerNameIndicator = serverNameIndicator;
    }

    public HostKeyRepository getHostKeyRepo() {
        return mHostKeyRepo;
    }

    public void setHostKeyRepo(HostKeyRepository hostKeyRepo) {
        mHostKeyRepo = hostKeyRepo;
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    public boolean isServerAddressLocked() {
        return serverAddressLocked;
    }

    public void setServerAddressLocked(boolean serverAddressLocked) {
        this.serverAddressLocked = serverAddressLocked;
    }

    public boolean isServerPortLocked() {
        return serverPortLocked;
    }

    public void setServerPortLocked(boolean serverPortLocked) {
        this.serverPortLocked = serverPortLocked;
    }

    public boolean isUsernameLocked() {
        return usernameLocked;
    }

    public void setUsernameLocked(boolean usernameLocked) {
        this.usernameLocked = usernameLocked;
    }

    public boolean isPasswordLocked() {
        return passwordLocked;
    }

    public void setPasswordLocked(boolean passwordLocked) {
        this.passwordLocked = passwordLocked;
    }

    public boolean isPrivateKeyLocked() {
        return privateKeyLocked;
    }

    public void setPrivateKeyLocked(boolean privateKeyLocked) {
        this.privateKeyLocked = privateKeyLocked;
    }

    public boolean isConnectionModeLocked() {
        return connectionModeLocked;
    }

    public void setConnectionModeLocked(boolean connectionModeLocked) {
        this.connectionModeLocked = connectionModeLocked;

    }

    public boolean isPreferIPv6() {
        return preferIPv6;
    }

    public SSHConfig setPreferIPv6(boolean preferIPv6) {
        this.preferIPv6 = preferIPv6;
        return this;
    }



    public Builder toBuilder(){
        Builder builder = new Builder(getName(),mConfigMode,getServerAddress(),getServerPort(),
                getUsername(),getPassword());
        builder.setJumper(mJumper)
                .setPrivateKey(mPrivateKey)
                .setUsePrivateKey(mUsePrivateKey)
                .setId(getId())
                .setPayload(mPayload)
                .setConnectionType(mConnectionType)
                .setUDPGWPort(mUDPGWPort)
                .setServerNameIndicator(mServerNameIndicator)
                .setServerAddressLocked(serverAddressLocked)
                .setServerPortLocked(serverPortLocked)
                .setUsernameLocked(usernameLocked)
                .setPasswordLocked(passwordLocked)
                .setPrivateKeyLocked(privateKeyLocked)
                .setConnectionModeLocked(connectionModeLocked)
                .setPreferIPv6(preferIPv6)
                .setRemoteSocksAddress(mRemoteSocksAddress)
                .setRemoteSocksPort(mRemoteSocksPort)
                .setUseRemoteSocksServer(useRemoteSocksServer);

        return builder;
    }
    public static class Builder {
        private String id;
        private String name;
        private int mConfigMode;
        private String mServerAddress;
        private int mServerPort;
        private String mUsername;
        private String mPassword;
        private boolean mUsePrivateKey;
        private PrivateKey mPrivateKey;
        private int mUDPGWPort;
        private SSHProxy mJumper;
        private int mConnectionType;
        private String mPayload;
        private String mServerNameIndicator;
        private boolean serverAddressLocked;
        private boolean serverPortLocked;
        private boolean usernameLocked;
        private boolean passwordLocked;
        private boolean privateKeyLocked;
        private boolean connectionModeLocked;
        private boolean preferIPv6;
        private boolean useRemoteSocksServer = true;
        private String mRemoteSocksAddress = "127.0.0.1";
        private int mRemoteSocksPort=1080;

        public Builder(int configMode) {
            mConfigMode = configMode;
        }

        public Builder(String name, int configMode, String serverAddress, int serverPort, String username,
                       String password) {
            this.name = name;
            mConfigMode = configMode;
            mServerAddress = serverAddress;
            mServerPort = serverPort;
            mUsername = username;
            mPassword = password;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setConfigMode(int configMode) {
            mConfigMode = configMode;
            return this;
        }

        public Builder setServerAddress(String serverAddress) {
            mServerAddress = serverAddress;
            return this;
        }

        public Builder setServerPort(int serverPort) {
            mServerPort = serverPort;
            return this;
        }

        public Builder setUsername(String username) {
            mUsername = username;
            return this;
        }

        public Builder setPassword(String password) {
            mPassword = password;
            return this;
        }

        public Builder setUsePrivateKey(boolean usePrivateKey) {
            mUsePrivateKey = usePrivateKey;
            return this;
        }

        public Builder setPrivateKey(PrivateKey privateKey) {
            mPrivateKey = privateKey;
            return this;
        }

        public Builder setUDPGWPort(int UDPGWPort) {
            mUDPGWPort = UDPGWPort;
            return this;
        }

        public Builder setJumper(SSHProxy jumper) {
            mJumper = jumper;
            return this;
        }

        public Builder setConnectionType(int connectionType) {
            mConnectionType = connectionType;
            return this;
        }

        public Builder setPayload(String payload) {
            mPayload = payload;
            return this;
        }

        public Builder setServerNameIndicator(String serverNameIndicator) {
            mServerNameIndicator = serverNameIndicator;
            return this;
        }

        public Builder setUseRemoteSocksServer(boolean useRemoteSocksServer) {
            this.useRemoteSocksServer = useRemoteSocksServer;
            return this;
        }

        public Builder setRemoteSocksAddress(String remoteSocksAddress) {
            mRemoteSocksAddress = remoteSocksAddress;
            return this;
        }

        public Builder setRemoteSocksPort(int remoteSocksPort) {
            mRemoteSocksPort = remoteSocksPort;
            return this;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getConfigMode() {
            return mConfigMode;
        }

        public String getServerAddress() {
            return mServerAddress;
        }

        public int getServerPort() {
            return mServerPort;
        }

        public String getUsername() {
            return mUsername;
        }

        public String getPassword() {
            return mPassword;
        }

        public boolean isUsePrivateKey() {
            return mUsePrivateKey;
        }

        public PrivateKey getPrivateKey() {
            return mPrivateKey;
        }

        public int getUDPGWPort() {
            return mUDPGWPort;
        }

        public SSHProxy getJumper() {
            return mJumper;
        }

        public int getConnectionType() {
            return mConnectionType;
        }

        public String getPayload() {
            return mPayload;
        }

        public String getServerNameIndicator() {
            return mServerNameIndicator;
        }

        public boolean isUseRemoteSocksServer() {
            return useRemoteSocksServer;
        }

        public String getRemoteSocksAddress() {
            return mRemoteSocksAddress;
        }

        public int getRemoteSocksPort() {
            return mRemoteSocksPort;
        }

        public SSHConfig build() {
            if (TextUtils.isEmpty(id)) {
                id = UUID.randomUUID().toString();
            }
            if (mConnectionType == 0) {
                mConnectionType = CONNECTION_TYPE_DIRECT;
            }
            if (mUDPGWPort == 0) {
                mUDPGWPort = DEFAULT_UDPGW_PORT;
            }

            SSHConfig config = new SSHConfig(name, id, CONFIG_TYPE,
                    mConfigMode, mServerAddress, mServerPort, mUsername,
                    mPassword, mUsePrivateKey, mPrivateKey, mUDPGWPort,
                    mJumper, mConnectionType, mPayload, mServerNameIndicator);
            config.passwordLocked = passwordLocked;
            config.usernameLocked = usernameLocked;
            config.serverAddressLocked = serverAddressLocked;
            config.serverPortLocked = serverPortLocked;
            config.privateKeyLocked = privateKeyLocked;
            config.connectionModeLocked = connectionModeLocked;
            config.setPreferIPv6(preferIPv6);
            config.useRemoteSocksServer = useRemoteSocksServer;
            config.mRemoteSocksAddress = mRemoteSocksAddress;
            config.mRemoteSocksPort = mRemoteSocksPort;

            return config;

        }


        public boolean isServerAddressLocked() {
            return serverAddressLocked;
        }

        public Builder setServerAddressLocked(boolean serverAddressLocked) {
            this.serverAddressLocked = serverAddressLocked;
            return this;
        }

        public boolean isServerPortLocked() {
            return serverPortLocked;
        }

        public Builder setServerPortLocked(boolean serverPortLocked) {
            this.serverPortLocked = serverPortLocked;
            return this;
        }

        public boolean isUsernameLocked() {
            return usernameLocked;
        }

        public Builder setUsernameLocked(boolean usernameLocked) {
            this.usernameLocked = usernameLocked;
            return this;
        }

        public boolean isPasswordLocked() {
            return passwordLocked;
        }

        public Builder setPasswordLocked(boolean passwordLocked) {
            this.passwordLocked = passwordLocked;
            return this;
        }

        public boolean isPrivateKeyLocked() {
            return privateKeyLocked;
        }

        public Builder setPrivateKeyLocked(boolean privateKeyLocked) {
            this.privateKeyLocked = privateKeyLocked;
            return this;
        }

        public boolean isConnectionModeLocked() {
            return connectionModeLocked;
        }

        public Builder setConnectionModeLocked(boolean connectionModeLocked) {
            this.connectionModeLocked = connectionModeLocked;
            return this;
        }

        public boolean isPreferIPv6() {
            return preferIPv6;
        }

        public Builder setPreferIPv6(boolean preferIPv6) {
            this.preferIPv6 = preferIPv6;
            return this;
        }
    }

}
