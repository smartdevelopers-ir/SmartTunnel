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
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;

import ir.smartdevelopers.smarttunnel.BuildConfig;
import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class DNSForwarder implements Runnable{
    private final DatagramPacket mPacket;
    private final String localDnsServerAddress;
    private final int localDnsServerPort;

    public DNSForwarder( DatagramPacket packet, String localDnsServerAddress, int localDnsServerPort)  {

        mPacket = packet;
        this.localDnsServerAddress = localDnsServerAddress;
        this.localDnsServerPort = localDnsServerPort;
    }

    @Override
    public void run() {
        byte[] udpData = Arrays.copyOfRange(mPacket.getData(),0,mPacket.getLength());
        byte[] tcpData = new byte[udpData.length + 2];
        byte[] lengthBytes = ByteUtil.getByteFromInt(udpData.length, 2);
        ArrayUtil.replace(tcpData, 0, lengthBytes);
        ArrayUtil.replace(tcpData, 2, udpData);
        try (Socket dnsServerSocket = new Socket();
             DatagramSocket mSocket = new DatagramSocket()){

            dnsServerSocket.setTcpNoDelay(true);
            dnsServerSocket.connect(new InetSocketAddress(localDnsServerAddress, localDnsServerPort));
            InputStream dnsIn = dnsServerSocket.getInputStream();
            OutputStream dnsOut = dnsServerSocket.getOutputStream();
            dnsOut.write(tcpData);
            dnsOut.flush();
            byte[] dnsResponseData = new byte[560];
            int len = dnsIn.read(dnsResponseData);

            if (len >0 ){
                byte[] tcpResponse = Arrays.copyOfRange(dnsResponseData,0,len);
                byte[] udpResponseData = Arrays.copyOfRange(tcpResponse,2,tcpResponse.length);
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
                mPacket.setData(udpResponseData,0,udpResponseData.length);
                mSocket.send(mPacket);
            }
            dnsIn.close();
            dnsOut.close();
        } catch (IOException e) {
            //ignore
        }
    }
}
