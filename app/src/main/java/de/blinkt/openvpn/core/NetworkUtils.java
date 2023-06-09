/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.provider.Settings;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class NetworkUtils {

    public static Vector<String> getLocalNetworks(Context c, boolean ipv6) {
        ConnectivityManager conn = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        Vector<String> nets = new Vector<>();
        Network[] networks = conn.getAllNetworks();
        for (Network network : networks) {
            NetworkInfo ni = conn.getNetworkInfo(network);
            LinkProperties li = conn.getLinkProperties(network);

            NetworkCapabilities nc = conn.getNetworkCapabilities(network);

            // Ignore network if it has no capabilities
            if (nc == null)
                continue;

            // Skip VPN networks like ourselves
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                continue;

            // Also skip mobile networks
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                continue;


            for (LinkAddress la : li.getLinkAddresses()) {
                if ((la.getAddress() instanceof Inet4Address && !ipv6) ||
                        (la.getAddress() instanceof Inet6Address && ipv6)) {
                        //nets.add(la.toString());
                    NetworkSpace.IpAddress ipaddress;
                    if (la.getAddress() instanceof Inet6Address)
                        ipaddress = new NetworkSpace.IpAddress((Inet6Address) la.getAddress(), la.getPrefixLength(), true);
                    else
                        ipaddress = new NetworkSpace.IpAddress(new CIDRIP(la.getAddress().getHostAddress(), la.getPrefixLength()), true);

                    nets.add(ipaddress.toString());
                }
            }
        }

        return nets;
    }

    @SuppressLint("HardwareIds")
    public static String getFakeMacAddrFromSAAID(Context c) {
        char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

        String saaid = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        if (saaid == null)
            return null;

        StringBuilder ret = new StringBuilder();
        if (saaid.length() >= 6) {
            byte[] sb = saaid.getBytes();
            for (int b = 0; b <= 6; b++) {
                if (b != 0)
                    ret.append(":");
                int v = sb[b] & 0xFF;
                ret.append(HEX_ARRAY[v >>> 4]);
                ret.append(HEX_ARRAY[v & 0x0F]);
            }
        }
        return ret.toString();
    }


    public static boolean isConnected(Context context) {
        ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = conn.getActiveNetworkInfo();
        if (info == null){
            return false;
        }
        return info.isConnected();
    }
    public static boolean isIPv6Enabled(){

        try {
            InetAddress[] addresses = InetAddress.getAllByName("google.com");
            if (addresses != null){
                for (InetAddress add : addresses){
                    if (add instanceof Inet6Address){
                        if (add.isReachable(100)){
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return false;
    }

    private static NetworkInterface findActiveInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()){
                NetworkInterface ni = interfaces.nextElement();
                if (Util.contains(ni.getDisplayName(),"lo") ||
                        Util.contains(ni.getDisplayName(),"p2p") ||
                        Util.contains(ni.getDisplayName(),"dummy") ||
                        Util.contains(ni.getDisplayName(),"sit")
                ){
                    continue;
                }
                List<InterfaceAddress> interfaceAddresses = ni.getInterfaceAddresses();
                if ( interfaceAddresses == null || interfaceAddresses.size()==0){
                    continue;
                }


            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
}