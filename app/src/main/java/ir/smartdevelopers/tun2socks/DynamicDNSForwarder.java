package ir.smartdevelopers.tun2socks;

import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;

import ir.smartdevelopers.smarttunnel.BuildConfig;
import ir.smartdevelopers.smarttunnel.channels.RemoteConnection;
import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class DynamicDNSForwarder implements Runnable{
    private final DatagramPacket mPacket;
    private final String dnsServerAddress;
    private final int dnsServerPort;
    private RemoteConnection mRemoteConnection;

    public DynamicDNSForwarder(DatagramPacket packet, String dnsServerAddress,
                               int dnsServerPort, RemoteConnection remoteConnection)  {

        mPacket = packet;
        this.dnsServerAddress = dnsServerAddress;
        this.dnsServerPort = dnsServerPort;
        mRemoteConnection = remoteConnection;
    }
    byte[] dnsResponseData;
    byte[] tcpResponse;
    byte[] udpResponseData;
    @Override
    public void run() {
        byte[] udpData = Arrays.copyOfRange(mPacket.getData(),0,mPacket.getLength());
        byte[] tcpData = new byte[udpData.length + 2];
        byte[] lengthBytes = ByteUtil.getByteFromInt(udpData.length, 2);
        ArrayUtil.replace(tcpData, 0, lengthBytes);
        ArrayUtil.replace(tcpData, 2, udpData);

        try (DatagramSocket mSocket = new DatagramSocket()){
            RemoteConnection.DirectTCPChannel channel = mRemoteConnection.startDirectTCPChannel(dnsServerAddress,dnsServerPort);
            InputStream dnsIn = channel.getRemoteIn();
            OutputStream dnsOut = channel.getRemoteOut();
            dnsOut.write(tcpData);
            dnsOut.flush();
             dnsResponseData = new byte[2048];
            int len = dnsIn.read(dnsResponseData);

            if (len >0 ){
                 tcpResponse = Arrays.copyOfRange(dnsResponseData,0,len);
                 udpResponseData = Arrays.copyOfRange(tcpResponse,2,tcpResponse.length);
                mPacket.setData(udpResponseData,0,udpResponseData.length);
                mSocket.send(mPacket);
                if (BuildConfig.DEBUG) {
                    Message sentMsg = new Message(udpData);

                    Message responseMsg = new Message(udpResponseData);
                    Record[] responseRecord = responseMsg.getSectionArray(Section.ANSWER);
                    String responseIp = null;
                    if (responseRecord != null && responseRecord.length >0 ){
                        responseIp = responseRecord[0].rdataToString();
                    }
                    Logger.logDebug(String.format(Locale.ENGLISH,"dns id sent is : %d dns id received is %d . RCODE is : %d ," +
                                    "question is : %s . answer is : %s",
                            sentMsg.getHeader().getID(),
                            responseMsg.getHeader().getID(),
                            responseMsg.getHeader().getRcode(),
                            sentMsg.getQuestion().toString(),
                            responseIp
                    ));
                }

            }
            dnsIn.close();
            dnsOut.close();
            mRemoteConnection.stopDirectTCPChannel(channel);
        }catch (Exception e){
            Logger.logDebug("dns timeout");
        }



//        Proxy proxy = new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",1080));
//        try (Socket dnsServerSocket = new Socket(proxy);
//             DatagramSocket mSocket = new DatagramSocket()){
//
//            dnsServerSocket.setTcpNoDelay(true);
//            dnsServerSocket.setSoTimeout(8000);
//            dnsServerSocket.connect(new InetSocketAddress("8.8.8.8", 53));
//            InputStream dnsIn = dnsServerSocket.getInputStream();
//            OutputStream dnsOut = dnsServerSocket.getOutputStream();
//            dnsOut.write(tcpData);
//            dnsOut.flush();
//            byte[] dnsResponseData = new byte[560];
//            int len = dnsIn.read(dnsResponseData);
//
//            if (len >0 ){
//                byte[] tcpResponse = Arrays.copyOfRange(dnsResponseData,0,len);
//                byte[] udpResponseData = Arrays.copyOfRange(tcpResponse,2,tcpResponse.length);
//                if (BuildConfig.DEBUG) {
//                    Message sentMsg = new Message(udpData);
//
//                    Message responseMsg = new Message(udpResponseData);
//                    Record[] responseRecord = responseMsg.getSectionArray(Section.ANSWER);
//                    String responseIp = null;
//                    if (responseRecord != null && responseRecord.length >0 ){
//                        responseIp = responseRecord[0].rdataToString();
//                    }
//                    Logger.logDebug(String.format(Locale.ENGLISH,"dns id sent is : %d dns id received is %d . RCODE is : %d ," +
//                                    "question is : %s . answer is : %s",
//                            sentMsg.getHeader().getID(),
//                            responseMsg.getHeader().getID(),
//                            responseMsg.getHeader().getRcode(),
//                            sentMsg.getQuestion().toString(),
//                            responseIp
//                    ));
//                }
//                mPacket.setData(udpResponseData,0,udpResponseData.length);
//                mSocket.send(mPacket);
//            }
//            dnsIn.close();
//            dnsOut.close();
//        } catch (IOException e) {
//            Logger.logDebug("dns timeout");
//        }
    }
}
