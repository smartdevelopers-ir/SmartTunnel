package ir.smartdevelopers.smarttunnel.ui.utils;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.util.Random;

import de.blinkt.openvpn.core.NetworkUtils;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class DNSUtil {
    public static String getIpV6(String hostName,String dnsServer){
        try{
            Record record6=Record.newRecord(Name.fromString(hostName + "."), Type.AAAA, DClass.IN);
            Message message6 = Message.newQuery(record6);
            Resolver r = new SimpleResolver(dnsServer);
            Message m6 = r.send(message6);
            Record[] records = m6.getSectionArray(Section.ANSWER);
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
        String ipv6 = null;
        boolean isDeviceSupportIpV6 = NetworkUtils.isIPv6Enabled();
        if (preferIpV6){
            if (!isDeviceSupportIpV6){
                Logger.logMessage(new LogItem("prefer IPv6 but device network not support it"));
            }
        }
        if (preferIpV6 && isDeviceSupportIpV6){
           ipv6 = getIpV6(host,dnsServer);
        }
        if (ipv6 != null){
            return ipv6;
        }
        return getIpV4(host,dnsServer);
    }
}
