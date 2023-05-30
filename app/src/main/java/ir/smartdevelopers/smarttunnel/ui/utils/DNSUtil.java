package ir.smartdevelopers.smarttunnel.ui.utils;

import android.app.admin.DnsEvent;
import android.net.DnsResolver;

import androidx.annotation.NonNull;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.RandomAccess;
import java.util.Vector;

import de.blinkt.openvpn.core.NetworkUtils;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class DNSUtil {
    public static String getRandomIpV6(String hostName,String dnsServer){
        try{
            String[] ips = getAllIPv6(hostName,dnsServer);
            if (ips != null && ips.length >0) {
                Random random = new Random();
                int randomIndex = random.nextInt(ips.length);
                return ips[randomIndex];
            }
        }catch (Exception e ){
           //ignore
        }
        return null;

    }
    public static String[] getAllIPv6(String hostName,String dnsServer){
        try{
            InetAddress[] all = InetAddress.getAllByName(hostName);
            boolean supportIpv6=false;
            if (all != null ){
                for (InetAddress addr : all){
                    if (addr instanceof Inet6Address){
                        supportIpv6 = true;
                        break;
                    }
                }
            }
            if (!supportIpv6 ){
                return null;
            }
            Record record6=Record.newRecord(Name.fromString(hostName + "."), Type.AAAA, DClass.IN);
            Message message6 = Message.newQuery(record6);
            Resolver r = new SimpleResolver(dnsServer);
            Message m6 = r.send(message6);
            Record[] records = m6.getSectionArray(Section.ANSWER);

            if (records != null && records.length >0) {
                String[] ips = new String[records.length];
                for (int i = 0 ;i < records.length;i++){
                    ips[i] = records[i].rdataToString();
                }
                return ips;
            }
        }catch (Exception e ){
            //ignore
        }
        return null;
    }
    public static String getIpV4(String hostName,String dnsServer){
        try{
            Record record4=Record.newRecord(Name.fromString(hostName + "."), Type.A, DClass.IN);
            Message message4 = Message.newQuery(record4);
            Resolver r = new SimpleResolver(dnsServer);
            Message m4 = r.send(message4);
            Record[] records = m4.getSectionArray(Section.ANSWER);
            if (records != null && records.length >0) {
                Random random = new Random();
                int randomIndex = random.nextInt(records.length);
                String ip = records[randomIndex].rdataToString();
                return ip;
            }
        }catch (Exception e ){
            //ignore
        }
        return null;

    }
    public static String getIp(String host,String dnsServer,boolean preferIpV6){
        boolean hostIsIp = false;
        if (!preferIpV6){
            String[] ips = host.split("\\.");
            if (ips.length == 4){
                try {
                    for (String s : ips){
                        Integer.parseInt(s);
                    }
                    hostIsIp = true;
                }catch (NumberFormatException e){
                    //ignore
                }
            }
        }else {
            String[] ips = host.split(":");
            if (ips.length <= 8){
                try {
                    for (String s : ips){
                        Integer.parseInt(s,16);
                    }
                    hostIsIp = true;
                }catch (NumberFormatException ignore){}
            }
        }
        if (hostIsIp){
            return host;
        }
        String ipv6 = null;
        if (preferIpV6){
           String[] allIPv6s = getAllIPv6(host,dnsServer);
           if (allIPv6s == null || allIPv6s.length ==0){
               Logger.logMessage(new LogItem("prefer IPv6 but but server dose not have IPv6"));
           }else {
               List<String> ipsQueue = new ArrayList<>(Arrays.asList(allIPv6s));
               Random random = new Random();
               while (ipsQueue.size() >0){
                   int randomIndex = random.nextInt(ipsQueue.size());
                   String randomIp = ipsQueue.get(randomIndex);
                   try {
                       Inet6Address address = (Inet6Address) Inet6Address.getByName(randomIp);
                       if (address.isReachable(400)){
                           ipv6 = randomIp;
                           break;
                       }
                       ipsQueue.remove(randomIndex);
                   } catch (Exception e) {
                       //ignore
                   }
               }
               if (ipv6 == null){
                   Logger.logMessage(new LogItem("prefer IPv6 but device network not" +
                           " supporting it or can't reach any of server IPv6s! try using IPv4"));
               }
           }

        }
        if (ipv6 != null){
            return ipv6;
        }
        return getIpV4(host,dnsServer);
    }
    public void  adsda(){

    }
}
