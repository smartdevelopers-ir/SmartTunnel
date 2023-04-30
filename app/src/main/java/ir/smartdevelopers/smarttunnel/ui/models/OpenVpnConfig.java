package ir.smartdevelopers.smarttunnel.ui.models;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSchException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.CIDRIP;
import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.NetworkSpace;
import de.blinkt.openvpn.core.NetworkUtils;
import de.blinkt.openvpn.core.OpenVPNManagement;
import de.blinkt.openvpn.core.OpenVPNThread;
import de.blinkt.openvpn.core.OpenVpnManagementThread;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;
import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.channels.RemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.receivers.NetworkStateReceiver;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class OpenVpnConfig extends Config implements IOpenVPNServiceInternal {
    public final static String CONFIG_TYPE = "OpenVPN";
    private static final String LOCAL_ADDRESS = "127.0.0.1";
    public final static String ORBOT_PACKAGE_NAME = "org.torproject.android";
    public static final String VPNSERVICE_TUN = "vpnservice-tun";

    private String mServerAddress;
    private int mServerPort;
    private String mUsername;
    private String mPassword;
    private boolean mUsePrivateKey;
    private PrivateKey mPrivateKey;
    private boolean serverAddressLocked;
    private boolean serverPortLocked;
    private boolean usernameLocked;
    private boolean passwordLocked;
    private boolean privateKeyLocked;
    private boolean preferIPv6;
    private VpnProfile mProfile;

    private transient HostKeyRepository mHostKeyRepo;
    private transient OpenVPNManagement mManagement;
    private transient final Object mProcessLock ;
    private transient Thread mProcessThread = null;
    private transient Runnable mOpenVPNThread;
    private transient RemoteConnection mRemoteConnection;
    private transient  int mLocalPort;
    private transient boolean mStarting;
    private transient NetworkStateReceiver mDeviceStateReceiver;
    private transient Handler guiHandler ;
    private transient CIDRIP mLocalIP = null;
    private transient int mMtu;
    private transient String mLocalIPv6 = null;
    private transient String mDomain = null;
    private transient Vector<String> mDnslist ;
    private transient NetworkSpace mRoutes ;
    private transient NetworkSpace mRoutesv6 ;
    private transient ProxyInfo mProxyInfo;
    private transient String mLastTunCfg;
    private transient String mRemoteGW;
    private transient Handler mRetryHandler;
    private transient ParcelFileDescriptor mParcelFileDescriptor;



    private transient final IBinder mBinder = new IOpenVPNServiceInternal.Stub() {

        @Override
        public boolean protect(int fd) throws RemoteException {
            return false;
        }

        @Override
        public void userPause(boolean shouldbePaused) throws RemoteException {
            OpenVpnConfig.this.userPause(shouldbePaused);
        }

        @Override
        public boolean stopVPN(boolean replaceConnection) throws RemoteException {
            return OpenVpnConfig.this.stopVPN(replaceConnection);
        }

        @Override
        public void addAllowedExternalApp(String packagename) throws RemoteException {

        }

        @Override
        public boolean isAllowedExternalApp(String packagename) throws RemoteException {
            return false;

        }

        @Override
        public void challengeResponse(String repsonse) throws RemoteException {

        }


    };

    /** This constructor is for instantiating from Gson*/
    public OpenVpnConfig(){
        super("","",OpenVpnConfig.CONFIG_TYPE);
        mProcessLock = new Object();

    }
    public OpenVpnConfig(String name, String id, String type, String serverAddress,
                         int serverPort, String username, String password,
                         boolean usePrivateKey, PrivateKey privateKey,VpnProfile profile) {
        super(name, id, type);
        mServerAddress = serverAddress;
        mServerPort = serverPort;
        mUsername = username;
        mPassword = password;
        mUsePrivateKey = usePrivateKey;
        mPrivateKey = privateKey;
        mProfile = profile;
        mProcessLock = new Object();


    }

    @Override
    public void connect() throws ConfigException {
        if (mState == State.CONNECTING){
            return;
        }
        init();

        VpnProfile profile = mProfile.copy("connectingProfile");
        try {

            PrivateKey privateKey = mPrivateKey;
            if (mUsePrivateKey && mPrivateKey != null) {
                privateKey = null;
            }
            String proxifiedAddress = mServerAddress;
            int proxifiedPort = mServerPort;

            disConnectPreviousConnection();
            if (getProxy() instanceof SSHProxy) {
                connectSSHProxy((SSHProxy) getProxy(), mServerAddress, mServerPort);
                proxifiedAddress = getProxy().getAddress();
                proxifiedPort = getProxy().getPort();

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
            JschRemoteConnection connection = new JschRemoteConnection(proxifiedAddress,proxifiedPort,
                    mUsername,mPassword, isServerAddressLocked(), isServerPortLocked(), isUsernameLocked(),
                    dns,preferIPv6);
            connection.setPrivateKey(privateKey);
            mRemoteConnection = connection;
            if (getProxy() instanceof HttpProxy) {
                mRemoteConnection.setProxy(getProxy());
            }
            mRemoteConnection.connect();
            Logger.logMessage(new LogItem("Connecting to OpenVPN server. please wait ..."));

            if (profile.mConnections == null || profile.mConnections.length == 0){
                mState = State.DISCONNECTING;
                cancel();
                throw new ConfigException("OpenVpn profile has no connection detail");
            }
            Connection con = profile.mConnections[0];
            int openVpnRemotePort = Integer.parseInt(con.mServerPort);
            String openVpnRemoteAddress = con.mServerName;
            Logger.logDebug(String.format(Locale.ENGLISH,"starting local port forwarding to openvpn server : %s:%d"
                    ,openVpnRemoteAddress,openVpnRemotePort));
            try {
                mLocalPort = mRemoteConnection.startLocalPortForwarding(LOCAL_ADDRESS,openVpnRemotePort,
                        openVpnRemoteAddress,openVpnRemotePort);
            } catch (RemoteConnectionException e) {
               mLocalPort = mRemoteConnection.startLocalPortForwarding(LOCAL_ADDRESS,0,
                       openVpnRemoteAddress,openVpnRemotePort);
            }
            Logger.logDebug(String.format(Locale.ENGLISH,"changing openVpn profile address and port to : %s:%d "
                    ,LOCAL_ADDRESS,mLocalPort));
            con.mServerName = LOCAL_ADDRESS;
            con.mServerPort = String.valueOf(mLocalPort);

            // open vpn init
            String nativeLibraryDirectory = mVpnService.getApplicationInfo().nativeLibraryDir;
            String tmpDir;
            try {
                tmpDir = mVpnService.getApplication().getCacheDir().getCanonicalPath();
            } catch (IOException e) {
                e.printStackTrace();
                tmpDir = "/tmp";
            }

            // Write OpenVPN binary
            String[] argv = VPNLaunchHelper.buildOpenvpnArgv(mVpnService);

            // Set a flag that we are starting a new VPN
            mStarting = true;
            // Stop the previous session by interrupting the thread.

            stopOldOpenVPNProcess();
            // An old running VPN should now be exited
            mStarting = false;
            // Start a new session by creating a new thread.
            boolean useOpenVPN3 = VpnProfile.doUseOpenVPN3(mVpnService);

            // Open the Management Interface
            if (!useOpenVPN3) {
                // start a Thread that handles incoming messages of the management socket
                OpenVpnManagementThread ovpnManagementThread = new OpenVpnManagementThread(this, mVpnService.getApplicationContext());
                if (ovpnManagementThread.openManagementInterface(mVpnService)) {
                    Thread mSocketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
                    mSocketManagerThread.start();
                    mManagement = ovpnManagementThread;
                    Logger.logInfo("started Socket Thread");
                } else {
                    cancel();
                    throw new ConfigException("Can not connect to OpenVpn client");
                }
            }
            Runnable processThread;
            if (useOpenVPN3) {
                OpenVPNManagement mOpenVPN3 = instantiateOpenVPN3Core();
                processThread = (Runnable) mOpenVPN3;
                mManagement = mOpenVPN3;
            } else {
                processThread = new OpenVPNThread(mVpnService, argv, nativeLibraryDirectory, tmpDir);
            }
            synchronized (mProcessLock) {
                mProcessThread = new Thread(processThread, "OpenVPNProcessThread");
                mProcessThread.start();
            }

            if (!useOpenVPN3) {
                try {
                    profile.writeConfigFileOutput(mVpnService.getApplicationContext(),
                            ((OpenVPNThread) processThread).getOpenVPNStdin());
                } catch (IOException | ExecutionException | InterruptedException e) {
                    Logger.logStyledMessage(String.format("Error generating config file %s", e.getMessage()),"#FFBA44",false);
                    mState = State.DISCONNECTING;
                    mVpnService.forceDisconnect();

                }
            }

        } catch (RemoteConnectionException | IOException e) {
            if (e instanceof RemoteConnectionException){
                if (((RemoteConnectionException) e).getCause() instanceof JSchException){
                    if (Util.contains(((RemoteConnectionException) e).getCause().getMessage(),"time out")){
                        Logger.logMessage(new LogItem("Time out when connecting to SSH server"));
                    }
                }
            }
            mState = State.DISCONNECTING;
            cancel();
            throw new ConfigException(e);
        }
    }

    private void disConnectPreviousConnection() throws RemoteConnectionException, IOException {
        if (mRemoteConnection != null){
            if (mRemoteConnection.isPortInUse(mLocalPort)){
                mRemoteConnection.stopLocalPortForwarding(LOCAL_ADDRESS,mLocalPort);
            }
            mRemoteConnection.disconnect();
            mRemoteConnection = null;
        }
        if (getProxy() instanceof SSHProxy){
            ((SSHProxy) getProxy()).getSSHConfig().cancel();
        }
        if (mParcelFileDescriptor != null){
            mParcelFileDescriptor.close();
            mParcelFileDescriptor = null;

        }
    }


    private void init() {
        mState = State.CONNECTING;
        mHostKeyRepo = new AcceptAllHostRepo();
        mRoutes = new NetworkSpace();
        mRoutesv6 = new NetworkSpace();
        mDnslist = new Vector<>();
        if (mRetryHandler == null){
            mRetryHandler = new Handler();
        }
        if (guiHandler == null) {
            guiHandler = new Handler(Looper.getMainLooper());
        }
    }


    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (mLocalIP != null)
            cfg += mLocalIP.toString();
        if (mLocalIPv6 != null)
            cfg += mLocalIPv6;


        cfg += "routes: " + TextUtils.join("|", mRoutes.getNetworks(true)) + TextUtils.join("|", mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", mRoutes.getNetworks(false)) + TextUtils.join("|", mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", mDnslist);
        cfg += "domain: " + mDomain;
        cfg += "mtu: " + mMtu;
        cfg += "proxyInfo: " + mProxyInfo;
        return cfg;
    }

    /**
     * we connect to proxy ssh server first, then we set local port forwarding
     * to destAddress and destPort
     */
    private void connectSSHProxy(SSHProxy proxy, String destAddress, int destPort) throws ConfigException {
        proxy.getSSHConfig().connect();
        RemoteConnection connection = proxy.getSSHConfig().getRemoteConnection();
        int localPort;
        try {
            localPort = connection.startLocalPortForwarding(proxy.getAddress(), proxy.getPort(), destAddress, destPort);
        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
        proxy.setPort(localPort);
    }


    @Override
    public Socket getMainSocket() {
        return mRemoteConnection.getMainSocket();
    }

    @Override
    public void retry() {
        if (mState == State.RECONNECTING || mState == State.CONNECTING ||
                mState == State.DISCONNECTED || mState == State.DISCONNECTING){
            return;
        }
        mState = State.RECONNECTING;
        if (mManagement != null){
            mManagement.stopVPN(true);
        }
        if (mVpnService != null){
            MyVpnService.reconnect(mVpnService.getApplicationContext());
        }
    }

    @Override
    public void cancel() {

        if (mState == State.DISCONNECTED){
            return;
        }
        if (mState != State.WAITING_FOR_NETWORK){
            mState = State.DISCONNECTED;
        }
        stopOldOpenVPNProcess();
        try {
            disConnectPreviousConnection();
        } catch (Exception ignore) {

        }
    }

    @Override
    public boolean isCanceled() {
        if (mState == State.DISCONNECTING || mState == State.DISCONNECTED){
            if (mState == State.WAITING_FOR_NETWORK){
                return false;
            }
            return true;
        }
        return false ;
    }




    private void stopOldOpenVPNProcess() {
        if (mManagement != null) {
            if (mOpenVPNThread != null)
                ((OpenVPNThread) mOpenVPNThread).setReplaceConnection();
            if (mManagement.stopVPN(false)) {
                // an old was asked to exit, wait 1s
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }

        forceStopOpenVpnProcess();
    }

    public void forceStopOpenVpnProcess() {
        synchronized (mProcessLock) {
            if (mProcessThread != null) {
                mProcessThread.interrupt();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

    private OpenVPNManagement instantiateOpenVPN3Core() {
        try {
            Class<?> cl = Class.forName("de.blinkt.openvpn.core.OpenVPNThreadv3");
            return (OpenVPNManagement) cl.getConstructor(Config.class, Context.class).newInstance(this, mVpnService.getApplicationContext());
        } catch (IllegalArgumentException | InstantiationException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getServerAddress() {
        return mServerAddress;
    }

    public OpenVpnConfig setServerAddress(String serverAddress) {
        mServerAddress = serverAddress;
        return this;
    }

    public int getServerPort() {
        return mServerPort;
    }

    public OpenVpnConfig setServerPort(int serverPort) {
        mServerPort = serverPort;
        return this;
    }

    public String getUsername() {
        return mUsername;
    }

    public OpenVpnConfig setUsername(String username) {
        mUsername = username;
        return this;
    }

    public String getPassword() {
        return mPassword;
    }

    public OpenVpnConfig setPassword(String password) {
        mPassword = password;
        return this;
    }

    public boolean isUsePrivateKey() {
        return mUsePrivateKey;
    }

    public OpenVpnConfig setUsePrivateKey(boolean usePrivateKey) {
        mUsePrivateKey = usePrivateKey;
        return this;
    }

    public PrivateKey getPrivateKey() {
        return mPrivateKey;
    }

    public OpenVpnConfig setPrivateKey(PrivateKey privateKey) {
        mPrivateKey = privateKey;
        return this;
    }

    public OpenVpnConfig setProfile(VpnProfile profile) {
        mProfile = profile;
        return this;
    }

    public boolean isServerAddressLocked() {
        return serverAddressLocked;
    }

    public OpenVpnConfig setServerAddressLocked(boolean serverAddressLocked) {
        this.serverAddressLocked = serverAddressLocked;
        return this;
    }

    public boolean isServerPortLocked() {
        return serverPortLocked;
    }

    public OpenVpnConfig setServerPortLocked(boolean serverPortLocked) {
        this.serverPortLocked = serverPortLocked;
        return this;
    }

    public boolean isUsernameLocked() {
        return usernameLocked;
    }

    public OpenVpnConfig setUsernameLocked(boolean usernameLocked) {
        this.usernameLocked = usernameLocked;
        return this;
    }

    public boolean isPasswordLocked() {
        return passwordLocked;
    }

    public OpenVpnConfig setPasswordLocked(boolean passwordLocked) {
        this.passwordLocked = passwordLocked;
        return this;
    }

    public boolean isPrivateKeyLocked() {
        return privateKeyLocked;
    }

    public OpenVpnConfig setPrivateKeyLocked(boolean privateKeyLocked) {
        this.privateKeyLocked = privateKeyLocked;
        return this;
    }

    public VpnProfile getProfile() {
        return mProfile;
    }

    public void requestInputFromUser(int password, String needed) {

    }

    public Builder toBuilder(){
        return new Builder()
                .setId(getId())
                .setName(getName())
                .setPrivateKey(mPrivateKey)
                .setUsePrivateKey(mUsePrivateKey)
                .setUsername(mUsername)
                .setPassword(mPassword)
                .setServerAddress(mServerAddress)
                .setServerPort(mServerPort)
                .setProfile(mProfile)
                .setPrivateKeyLocked(privateKeyLocked)
                .setServerAddressLocked(serverAddressLocked)
                .setServerPortLocked(serverPortLocked)
                .setUsernameLocked(usernameLocked)
                .setPasswordLocked(passwordLocked)
                .setPreferIPv6(preferIPv6);

    }

    @Override
    public boolean protect(int fd) throws RemoteException {
        return true;
    }

    @Override
    public void userPause(boolean shouldBePaused) throws RemoteException {

    }

    @Override
    public boolean stopVPN(boolean replaceConnection) throws RemoteException {
        if (mVpnService != null){
            MyVpnService.disconnect(mVpnService.getApplicationContext(),false);
        }
        return true;
    }

    @Override
    public void addAllowedExternalApp(String packagename) throws RemoteException {

    }

    @Override
    public boolean isAllowedExternalApp(String packagename) throws RemoteException {
        return false;
    }

    @Override
    public void challengeResponse(String repsonse) throws RemoteException {
        if (mManagement != null) {
            String b64response = Base64.encodeToString(repsonse.getBytes(Charset.forName("UTF-8")), Base64.DEFAULT);
            mManagement.sendCRResponse(b64response);
        }
    }

    @Override
    public IBinder asBinder() {
        return mBinder;
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        mLocalIP = new CIDRIP(local, netmask);
        mMtu = mtu;
        mRemoteGW = null;

        long netMaskAsInt = CIDRIP.getInt(netmask);

        if (mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP

            int masklen;
            long mask;
            if ("net30".equals(mode)) {
                masklen = 30;
                mask = 0xfffffffc;
            } else {
                masklen = 31;
                mask = 0xfffffffe;
            }

            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if ((netMaskAsInt & mask) == (mLocalIP.getInt() & mask)) {
                mLocalIP.len = masklen;
            } else {
                mLocalIP.len = 32;
                if (!"p2p".equals(mode))
                    Logger.logWarning(mVpnService.getString(R.string.ip_not_cidr,local, netmask, mode) );
            }
        }
        if (("p2p".equals(mode) && mLocalIP.len < 32) || ("net30".equals(mode) && mLocalIP.len < 30)) {
            Logger.logWarning(String.format("Vpn topology \\\"%3$s\\\" specified but ifconfig %1$s %2$s looks more like an IP address with a network mask. Assuming \\\"subnet\\\" topology", local, netmask, mode));
        }


        /* Workaround for Lollipop and higher, it does not route traffic to the VPNs own network mask */
        if (mLocalIP.len <= 31) {
            CIDRIP interfaceRoute = new CIDRIP(mLocalIP.mIp, mLocalIP.len);
            interfaceRoute.normalise();
            addRoute(interfaceRoute, true);
        }


        // Configurations are sometimes really broken...
        mRemoteGW = netmask;
    }
    public void addRoute(CIDRIP route, boolean include) {
        mRoutes.addIP(route, include);
    }
    public void addRoute(String dest, String mask, String gateway, String device) {
        CIDRIP route = new CIDRIP(dest, mask);
        boolean include = isAndroidTunDevice(device);

        NetworkSpace.IpAddress gatewayIP = new NetworkSpace.IpAddress(new CIDRIP(gateway, 32), false);

        if (mLocalIP == null) {
            VpnStatus.logError("Local IP address unset and received. Neither pushed server config nor local config specifies an IP addresses. Opening tun device is most likely going to fail.");
            return;
        }
        NetworkSpace.IpAddress localNet = new NetworkSpace.IpAddress(mLocalIP, true);
        if (localNet.containsNet(gatewayIP))
            include = true;

        if (gateway != null &&
                (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include = true;


        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            Logger.logWarning(mVpnService.getString(R.string.route_not_cidr, dest, mask));
        }

        if (route.normalise())
            Logger.logWarning(mVpnService.getString(R.string.route_not_netip, dest, route.len, route.mIp));

        mRoutes.addIP(route, include);
    }
    public void setLocalIPv6(String ipv6addr) {
        mLocalIPv6 = ipv6addr;
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }
    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(mLastTunCfg)) {
            return "NOACTION";
        } else {
            return "OPEN_BEFORE_CLOSE";
        }
    }

    public void addRoutev6(String network, String device) {
        // Tun is opened after ROUTE6, no device name may be present
        boolean included = isAndroidTunDevice(device);
        addRoutev6(network, included);
    }

    public void addRoutev6(String network, boolean included) {
        String[] v6parts = network.split("/");

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logException(e);
        }

    }
    private boolean isAndroidTunDevice(String device) {
        return device != null &&
                (device.startsWith("tun") || "(null)".equals(device) || VPNSERVICE_TUN.equals(device));
    }

    @Override
    public ParcelFileDescriptor getFileDescriptor() {
        mParcelFileDescriptor = openTun();
       return mParcelFileDescriptor;
    }
    public ParcelFileDescriptor openTun(){
        VpnService.Builder builder = mVpnService.getBuilder();
        if (mLocalIP == null && mLocalIPv6 == null) {
            Logger.logError(mVpnService.getString(R.string.opentun_no_ipaddr));
            return null;
        }
        if (mLocalIP != null) {
            // OpenVPN3 manages excluded local networks by callback
            if (!VpnProfile.doUseOpenVPN3(mVpnService.getApplication()))
                addLocalNetworksToRoutes();
            try {
                builder.addAddress(mLocalIP.mIp, mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                Logger.logError("add route error" + iae.getLocalizedMessage());
                return null;
            }
        }

        if (mLocalIPv6 != null) {
            String[] ipv6parts = mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                Logger.logError("add route error" + iae.getLocalizedMessage());
                return null;
            }

        }
        builder.setSession("SmartTunnel").setMtu(mMtu);
        Collection<NetworkSpace.IpAddress> positiveIPv4Routes = mRoutes.getPositiveIPList();
        Collection<NetworkSpace.IpAddress> positiveIPv6Routes = mRoutesv6.getPositiveIPList();

        if ("samsung".equals(Build.BRAND) && mDnslist.size() >= 1) {
            // Check if the first DNS Server is in the VPN range
            try {
                NetworkSpace.IpAddress dnsServer = new NetworkSpace.IpAddress(new CIDRIP(mDnslist.get(0), 32), true);
                boolean dnsIncluded = false;
                for (NetworkSpace.IpAddress net : positiveIPv4Routes) {
                    if (net.containsNet(dnsServer)) {
                        dnsIncluded = true;
                    }
                }
                if (!dnsIncluded) {
                    String samsungwarning = String.format("Warning Samsung Android 5.0+ devices ignore DNS servers outside the VPN range. To enable DNS resolution a route to your DNS Server (%s) has been added.", mDnslist.get(0));
                    VpnStatus.logWarning(samsungwarning);
                    positiveIPv4Routes.add(dnsServer);
                }
            } catch (Exception e) {
                // If it looks like IPv6 ignore error
                if (!mDnslist.get(0).contains(":"))
                    VpnStatus.logError("Error parsing DNS Server IP: " + mDnslist.get(0));
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            installRoutesExcluded(builder, mRoutes);
            installRoutesExcluded(builder, mRoutesv6);
        } else {
            installRoutesPostiveOnly(builder, positiveIPv4Routes, positiveIPv6Routes);
        }


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
        builder.addDnsServer("8.8.8.8");

        mLastTunCfg = getTunConfigString();
        // Reset information
        mDnslist.clear();
        mRoutes.clear();
        mRoutesv6.clear();
        mLocalIP = null;
        mLocalIPv6 = null;
        mDomain = null;
        mProxyInfo = null;
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
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void installRoutesExcluded(VpnService.Builder builder, NetworkSpace routes)
    {
        for(NetworkSpace.IpAddress ipIncl: routes.getNetworks(true))
        {
            try {
                builder.addRoute(ipIncl.getPrefix());
            } catch (UnknownHostException|IllegalArgumentException ia) {
                VpnStatus.logError(mVpnService.getString(R.string.route_rejected) + ipIncl + " " + ia.getLocalizedMessage());
            }
        }
        for(NetworkSpace.IpAddress ipExcl: routes.getNetworks(false))
        {
            try {
                builder.excludeRoute(ipExcl.getPrefix());
            } catch (UnknownHostException|IllegalArgumentException ia) {
                VpnStatus.logError(mVpnService.getString(R.string.route_rejected) + ipExcl + " " + ia.getLocalizedMessage());
            }
        }
    }

    private void installRoutesPostiveOnly(VpnService.Builder builder, Collection<NetworkSpace.IpAddress> positiveIPv4Routes, Collection<NetworkSpace.IpAddress> positiveIPv6Routes) {
        NetworkSpace.IpAddress multicastRange = new NetworkSpace.IpAddress(new CIDRIP("224.0.0.0", 3), true);

        for (NetworkSpace.IpAddress route : positiveIPv4Routes) {
            try {
                if (multicastRange.containsNet(route))
                    VpnStatus.logDebug(R.string.ignore_multicast_route, route.toString());
                else
                    builder.addRoute(route.getIPv4Address(), route.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(mVpnService.getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (NetworkSpace.IpAddress route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(mVpnService.getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }
    }

    private void addLocalNetworksToRoutes() {
        for (String net : NetworkUtils.getLocalNetworks(mVpnService, false)) {
            String[] netparts = net.split("/");
            String ipAddr = netparts[0];
            int netMask = Integer.parseInt(netparts[1]);
            if (ipAddr.equals(mLocalIP.mIp))
                continue;

            if(mProfile.mAllowLocalLAN)
                mRoutes.addIP(new CIDRIP(ipAddr, netMask), false);
        }

        if (mProfile.mAllowLocalLAN) {
            for (String net : NetworkUtils.getLocalNetworks(mVpnService, true)) {
                addRoutev6(net, false);
            }
        }
    }

    public void onOpenVpnConnected() {
        mVpnService.onConnect();
        mState = State.CONNECTED;
    }

    public void onOpenVpnDisconnected() {
        if (isCanceled()){
            return;
        }
        mState = State.DISCONNECTING;
        Logger.logInfo("OpenVPN disconnected asking MyVpnService to reconnect ... ");
        MyVpnService.reconnect(mVpnService.getApplicationContext());
    }

    public void onOpenVpnReconnecting() {
        Logger.logInfo("OpenVPN engine told us it is reconnecting ");
        if(mRemoteConnection.isConnected()) {
           guiHandler.post(()->{
               mVpnService.onReconnecting();
           });
        } else if (mState == State.WAITING_FOR_NETWORK || !NetworkUtils.isConnected(mVpnService)) {
            Logger.logInfo("There is no network! so we just waiting ");
        } else {
            Logger.logInfo("the remoteConnection is not connected. We ask Config to retry ");
            retry();

        }
    }



    public boolean isConnected() {
        if (mRemoteConnection == null){
            return false;
        }
        return  mRemoteConnection.isConnected();
    }

    public boolean isPreferIPv6() {
        return preferIPv6;
    }

    public OpenVpnConfig setPreferIPv6(boolean preferIPv6) {
        this.preferIPv6 = preferIPv6;
        return this;
    }

    public static class Builder {
        private String id;
        private String name;
        private String mServerAddress;
        private int mServerPort;
        private String mUsername;
        private String mPassword;
        private boolean mUsePrivateKey;
        private PrivateKey mPrivateKey;
        private boolean serverAddressLocked;
        private boolean serverPortLocked;
        private boolean usernameLocked;
        private boolean passwordLocked;
        private boolean privateKeyLocked;
        private boolean preferIPv6;

        private VpnProfile mProfile;

        public String getId() {
            return id;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public String getServerAddress() {
            return mServerAddress;
        }

        public Builder setServerAddress(String serverAddress) {
            mServerAddress = serverAddress;
            return this;
        }

        public int getServerPort() {
            return mServerPort;
        }

        public Builder setServerPort(int serverPort) {
            mServerPort = serverPort;
            return this;
        }

        public String getUsername() {
            return mUsername;
        }

        public Builder setUsername(String username) {
            mUsername = username;
            return this;
        }

        public String getPassword() {
            return mPassword;
        }

        public Builder setPassword(String password) {
            mPassword = password;
            return this;
        }

        public boolean isUsePrivateKey() {
            return mUsePrivateKey;
        }

        public Builder setUsePrivateKey(boolean usePrivateKey) {
            mUsePrivateKey = usePrivateKey;
            return this;
        }

        public PrivateKey getPrivateKey() {
            return mPrivateKey;
        }

        public Builder setPrivateKey(PrivateKey privateKey) {
            mPrivateKey = privateKey;
            return this;
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

        public VpnProfile getProfile() {
            return mProfile;
        }

        public Builder setProfile(VpnProfile profile) {
            mProfile = profile;
            return this;
        }
        public boolean isPreferIPv6() {
            return preferIPv6;
        }

        public Builder setPreferIPv6(boolean preferIPv6) {
            this.preferIPv6 = preferIPv6;
            return this;
        }
        public OpenVpnConfig build(){
            if (TextUtils.isEmpty(id)) {
                id = UUID.randomUUID().toString();
            }
            OpenVpnConfig config = new OpenVpnConfig(name,id,OpenVpnConfig.CONFIG_TYPE,
                    mServerAddress,mServerPort,mUsername,mPassword,
                    mUsePrivateKey,mPrivateKey,mProfile);
            config.serverAddressLocked = serverAddressLocked;
            config.serverPortLocked = serverPortLocked;
            config.usernameLocked = usernameLocked;
            config.passwordLocked = passwordLocked;
            config.privateKeyLocked = privateKeyLocked;
            config.preferIPv6 = preferIPv6;
            return config;
        }


    }

    public static OpenVpnConfig fromJson(String json){
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(OpenVpnConfig.class,new GsonInstanceCreator());
        Gson gson = builder.create();
        return gson.fromJson(json,OpenVpnConfig.class);
    }
    public static class GsonInstanceCreator implements InstanceCreator<OpenVpnConfig>{

        @Override
        public OpenVpnConfig createInstance(Type type) {
            return new OpenVpnConfig();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenVpnConfig config = (OpenVpnConfig) o;
        return mServerPort == config.mServerPort && mUsePrivateKey == config.mUsePrivateKey && Objects.equals(mServerAddress, config.mServerAddress) && Objects.equals(mUsername, config.mUsername) && Objects.equals(mPassword, config.mPassword) && Objects.equals(mPrivateKey, config.mPrivateKey) && Objects.equals(mProfile, config.mProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mServerAddress, mServerPort, mUsername, mPassword, mUsePrivateKey, mPrivateKey, mProfile);
    }
}
