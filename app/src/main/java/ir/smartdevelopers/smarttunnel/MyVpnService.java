package ir.smartdevelopers.smarttunnel;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.codec.binary.BinaryCodec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocolFactory;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import socks.Socks5Proxy;
import socks.SocksServerSocket;

public class MyVpnService extends VpnService {
    private ExecutorService mExecutorService = Executors.newCachedThreadPool();
    PipedOutputStream systemOut = new PipedOutputStream();
    PipedInputStream systemIn = new PipedInputStream();
    private PipedOutputStream outWriter = new PipedOutputStream();
    /**Socks server reads from this that other write to their out*/
    private PipedInputStream socksSelfInputStream = new PipedInputStream();
    /**This is socks outputStream that provides to others , so they can write to this*/
    private PipedOutputStream socksOutputStream = new PipedOutputStream();
    /**Socks server will write to this for its output*/
    private PipedOutputStream socksSelfOutputStream = new PipedOutputStream();
    /**This is socks inputStream that provides to others , so they can read from this*/
    private PipedInputStream socksInputStream = new PipedInputStream();
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    ParcelFileDescriptor vpnInterface = null;
    private static final String PRIVATE_VLAN4_CLIENT = "10.0.0.1";
    private static final String PRIVATE_VLAN4_ROUTER = "10.0.0.2";

    private static final String PRIVATE_VLAN6_CLIENT = "fc00::1";
    private static final String PRIVATE_VLAN6_ROUTER = "fc00::2";

    private static final String PRIVATE_NETMASK = "255.255.255.252";

    private static final int PRIVATE_MTU = 1500;

    private ConnectivityManager connectivityManager;
    private InputStream remoteIn = null;
    private OutputStream remoteOut = null;

    private PacketManager mPacketManager;
    @Override
    public void onCreate() {
        super.onCreate();
//        MainNative.initialize(getApplicationContext());
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            systemIn.connect(outWriter);
            socksOutputStream.connect(socksSelfInputStream);
//            socksOutputStream.write(0);
            socksInputStream.connect(socksSelfOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String host = intent.getStringExtra("host");
        String user = intent.getStringExtra("user");
        String pass = intent.getStringExtra("pass");
        int port = intent.getIntExtra("port",22);
        connect(host,port,user,pass);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    final byte[] packet = new byte[1024*4];
    ChannelDirectTCPIP channel ;
    public void connect(String host, int port, String user, String pass){
        String knownHostFile = getKnownHostPath(getApplicationContext());
        HostKeyRepo hostKeyRepo = new HostKeyRepo(getApplicationContext());
        makeForeground();
        mExecutorService.execute(()->{
            try (PipedInputStream in = new PipedInputStream();){
                systemOut.connect(in);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                while (true){
                    String message = reader.readLine();
                    Log.v("TTT","message :"+message);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        });



        mExecutorService.execute(new Runnable() {
                     @Override
                     public void run() {
//                         Connection connection = new Connection("5.45.64.41",port);


                         JSch jSch = new JSch();
                         jSch.setHostKeyRepository(hostKeyRepo);
                         Session session = null;
                         try {
                             jSch.setKnownHosts(knownHostFile);
//
                             session = jSch.getSession(user, host, port);
//
                             session.setPassword(pass);
//
                             session.connect(30000);



                             VpnService.Builder builder=new VpnService.Builder();
                             builder.setSession("Tun2Socks").setMtu(PRIVATE_MTU);
                             builder.addAddress(PRIVATE_VLAN4_CLIENT, 30);
//                             builder.addAllowedApplication("com.android.chrome");
                             builder.addAllowedApplication("ir.smartdevelopers.tcptest");
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                 Network activeNetwork = connectivityManager.getActiveNetwork();
                                 if (activeNetwork != null) {
                                     builder.setUnderlyingNetworks(new Network[] {activeNetwork});
                                 }
                             }
//                             builder.addAllowedApplication("ir.smartdevelopers.tcptest");
                             builder.addDnsServer("8.8.8.8");
                             builder.addRoute("0.0.0.0", 0);
                             vpnInterface = builder.establish();

                             SystemClock.sleep(1000);


                             FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                             mPacketManager = new PacketManager(session);
                             final long IDLE_TIME = 100;
                             mExecutorService.execute(()->{
                                 int len=0;
                                 int i= 0;


                                 try{
                                     OutputStream packetManagerOut = mPacketManager.getOutputStream();
                                     while (true){
                                         boolean idle = true;
                                         clear(packet);
                                         len=in.read(packet);
                                         if (len >0){
                                             packetManagerOut.write(packet,0,len);
                                             packetManagerOut.flush();
                                             idle=false;
                                         }

                                         if (idle){
                                             Thread.sleep(IDLE_TIME);
                                         }
                                     }
                                 } catch (IOException e) {
                                     throw new RuntimeException(e);
                                 } catch (InterruptedException e) {
                                     throw new RuntimeException(e);
                                 }
                             });

                             mExecutorService.execute(()->{
                                 InputStream packageManagerIn = mPacketManager.getInputStream();
                                 int len = 0;
                                 byte[] buffer = new byte[Packet.MAX_SIZE];
                                 boolean idle= true;
                                 try{
                                     while (true){
                                         len = packageManagerIn.read(buffer);
                                         if (len > 0) {
                                             out.write(buffer, 0, len);
                                             out.flush();
                                             idle = false;
                                         }
                                         if (idle){
                                             Thread.sleep(IDLE_TIME);
                                         }

                                     }
                                 } catch (IOException e) {
                                     throw new RuntimeException(e);
                                 } catch (InterruptedException e) {
                                     throw new RuntimeException(e);
                                 }
                             });


                         }  catch (Exception e) {

                             stopForeground(true);
                             stopSelf();
                             e.printStackTrace();
                         }
                     }
                 }
        );
    }

    private void clear(byte[] packet) {
        Arrays.fill(packet, (byte) 0);
    }

    private PacketDetail getPacketDetail(byte[] packet,int packetLength){
        PacketDetail detail = new PacketDetail();
        String ipHeaderVersionAndLengthBits = getBitString(new byte[]{packet[0]});
        detail.version = Integer.parseInt(ipHeaderVersionAndLengthBits.substring(0,4),2);
        if (detail.version != 4){
            return detail;
        }
        detail.ipHeaderLength = Integer.parseInt(ipHeaderVersionAndLengthBits.substring(4,8),2);
        int sourceAddrStartIndex = 12;// index in byte array
        int destAddrStartIndex = 16;// index in byte array
        String sourceAddrBits = getBitString(Arrays.copyOfRange(packet,sourceAddrStartIndex,sourceAddrStartIndex+4));
        String destAddrBits = getBitString(Arrays.copyOfRange(packet,destAddrStartIndex,destAddrStartIndex+4));
        StringBuilder sourceIp= new StringBuilder();
        StringBuilder destIp=new StringBuilder();
        byte[] sourceIpByte=new byte[4];
        byte[] destIpByte=new byte[4];
        int ipNumber=0;
        for (int i=0 ; i<4 ;i++){
            // source ip
            ipNumber = Integer.parseInt(sourceAddrBits.substring(i*8,(i+1)*8),2);
            sourceIp.append(ipNumber);
            sourceIpByte[i]=(byte) ipNumber;
            // dest ip
            ipNumber = Integer.parseInt(destAddrBits.substring(i*8,(i+1)*8),2);
            destIp.append(ipNumber);
            destIpByte[i]=(byte) ipNumber;
            if (i < 3){
                sourceIp.append(".");
                destIp.append(".");
            }
        }
        detail.sourceIp = sourceIp.toString();
        detail.sourceIpByte = sourceIpByte;
        detail.destIp = destIp.toString();
        detail.destIpByte = destIpByte;

        int ipHeaderEndIndex = detail.ipHeaderLength * 32 / 8;// index in packet per byte
        detail.ipHeader = Arrays.copyOfRange(packet,0,ipHeaderEndIndex);
        byte[] remaining=Arrays.copyOfRange(packet,ipHeaderEndIndex,packetLength);
        int tcpHeaderLengthByteIndex = 3*4;
        String tcpHeaderLengthBits = getBitString(new byte[]{remaining[tcpHeaderLengthByteIndex]});
        detail.tcpHeaderLength = Integer.parseInt(tcpHeaderLengthBits.substring(0 , 4),2);
        detail.tcpHeader = Arrays.copyOfRange(remaining,0,(detail.tcpHeaderLength *32 / 8));
        byte[] sourcePortBytes = Arrays.copyOfRange(remaining,0,2);
        byte[] destPortBytes = Arrays.copyOfRange(remaining,2,4);
        detail.sourcePort = Integer.parseInt(getBitString(sourcePortBytes),2);
        detail.destPort = Integer.parseInt(getBitString(destPortBytes),2);
        detail.sequenceNumberByte = Arrays.copyOfRange(detail.tcpHeader,4,8);
        detail.sequenceNumber = new BigInteger(detail.sequenceNumberByte).intValue();
        detail.acknowledgmentNumberByte = Arrays.copyOfRange(detail.tcpHeader,8,12);
        detail.acknowledgmentNumber = new BigInteger(detail.acknowledgmentNumberByte).intValue();
        detail.syn = (byte) ((detail.tcpHeader[13] & 0b0010 )>>> 1);
        detail.ack = (byte) ((detail.tcpHeader[13] & 0b00010000 )>>> 4);
        detail.data = Arrays.copyOfRange(remaining,detail.tcpHeader.length,remaining.length);
        return  detail;

    }

    private byte[] calculateIPHeader(byte[] header,byte[] sourceIp,byte[] destIp){
        ByteBuffer buffer = ByteBuffer.allocate(header.length);
        buffer.put(Arrays.copyOfRange(header,0,12));
        buffer.put(sourceIp);
        buffer.put(destIp);
        byte[] options = Arrays.copyOfRange(header,20,header.length);
        if (options.length > 0){
            buffer.put(options);
        }
        // change checksum to 0
        buffer.array()[10] = 0;
        buffer.array()[11] = 0;
         byte[] checksum = calculateChecksum(buffer.array());
//         buffer.array()[10] = checksum[0];
//         buffer.array()[11] = checksum[1];
        ArrayReplace(buffer.array(),10,checksum);
         return buffer.array();

    }
    private byte[] calculateTCPPseudoHeader(byte[] tcpHeader,byte[] sourceIp,int sourcePort,byte[] destIp,int destPort,
                                            byte protocol,byte[] tcpData,int sequenceNumber,int syn,int ack){

        ByteBuffer buffer = ByteBuffer.allocate(tcpHeader.length + tcpData.length + 12);
        buffer.put(sourceIp);
        buffer.put(destIp);
        buffer.put(new byte[]{0});
        buffer.put(new byte[]{protocol});
        //calculate tcp segment length in 16 bit
        int tcpLength = tcpHeader.length + tcpData.length;

        byte[] tcpLengthBytes=getByteFromInt(tcpLength,2);
        buffer.put(tcpLengthBytes);
        byte[] tcpHeaderCopy = Arrays.copyOf(tcpHeader,tcpHeader.length);
        // set portNumbers
        byte[] sPort = getByteFromInt(sourcePort,2);
        byte[] dPort = getByteFromInt(destPort,2);
        ArrayReplace(tcpHeaderCopy,0,sPort);
//        tcpHeaderCopy[0]=sPort[0];
//        tcpHeaderCopy[1]=sPort[1];
        ArrayReplace(tcpHeaderCopy,2,dPort);
//        tcpHeaderCopy[2]=dPort[0];
//        tcpHeaderCopy[3]=dPort[1];
        //set header checksum to 0
        tcpHeaderCopy[16]=0;
        tcpHeaderCopy[17]=0;
        if (syn == 1 && ack ==0) {
            // add 1 to sequence number
            // set sequence number+1 to acknowledgment number
            // set random number to sequence number
            // set ack to 1
            byte[] acknowledgment = getByteFromInt(sequenceNumber+1,4);
            Random random =new Random();
            byte[] newSecNumber = getByteFromInt(random.nextInt(),4);
            ArrayReplace(tcpHeaderCopy,8,acknowledgment);
//            tcpHeaderCopy[8]=acknowledgment[0];
//            tcpHeaderCopy[9]=acknowledgment[1];
//            tcpHeaderCopy[10]=acknowledgment[2];
//            tcpHeaderCopy[11]=acknowledgment[3];

            ArrayReplace(tcpHeaderCopy,4,newSecNumber);
//            tcpHeaderCopy[4]=newSecNumber[0];
//            tcpHeaderCopy[5]=newSecNumber[1];
//            tcpHeaderCopy[6]=newSecNumber[2];
//            tcpHeaderCopy[7]=newSecNumber[3];

            // flags index byte is 13
            tcpHeaderCopy[13] = (byte) (tcpHeaderCopy[13] | (1<<4));
        }
        buffer.put(tcpHeaderCopy);
        buffer.put(tcpData);
        byte[] checksum = calculateChecksum(buffer.array());

        ArrayReplace(buffer.array(),28,checksum);
//        buffer.array()[28] = checksum[0];
//        buffer.array()[29] = checksum[1];
//        tcpHeaderCopy[16] = checksum[0];
//        tcpHeaderCopy[17] = checksum[1];
        ArrayReplace(tcpHeaderCopy,16,checksum);

        ByteBuffer r = ByteBuffer.allocate(tcpHeader.length + tcpData.length);
        r.put(tcpHeaderCopy);
        r.put(tcpData);
        return r.array();
    }

    public static void ArrayReplace(byte[] source,int startIndex,byte[] data){
        if (source.length < startIndex + data.length){
            throw new ArrayIndexOutOfBoundsException("source array length is less than data length from index");
        }
        int n=0;
        for (int i = startIndex;i<startIndex+data.length;i++){
            source[i]=data[n++];
        }
    }
    private byte[] calculateChecksum(byte[] data) {
        if (data.length % 2 !=0){
            data=Arrays.copyOf(data,data.length+1);
        }
        int sum=0;

        for (int i = 0 ; i<data.length;i+=2){
            int b2 = ((data[i] & 0xFF ) << 8)  | (data[i+1] & 0xFF);
            int r = (sum + b2);
            if (r > 0xFFFF){
                r = r & 0xFFFF;
                r+=1;
            }
            sum = r;

        }
        return getByteFromInt((~sum & 0xFFFF),2);
    }
    public  byte[] getByteFromInt(int value,int minByteArraySize){
        int leadingZero = Integer.numberOfLeadingZeros(value);
        int byteCount = 4 - leadingZero/8;
        byteCount = Integer.max(byteCount,minByteArraySize);
        byte[] r = new byte[byteCount];
        for (int i = byteCount-1 ; i >= 0 ; i--){
            byte a = (byte) (value & 0xFF);
            r[i] = a;
            value >>>= 8;
        }
        return r;
    }

    private String getBitString(byte[] data){
        if (data == null){
            return null;
        }

        return BinaryCodec.toAsciiString(flip(data));
    }
    public byte[] flip(byte[] data){
        byte[] result = new byte[data.length];
        int index=0;
        for (int i=data.length-1;i>=0;i--){
            result[index]=data[i];
            index++;
        }
        return result;
    }
    static class PacketDetail{
        int version ;
        /**Internet Header Length is the length of the internet header in 32
         bit words, and thus points to the beginning of the data*/
        int ipHeaderLength;
        String sourceIp;
        byte[] sourceIpByte;
        String destIp;
        byte[] destIpByte;
        int sourcePort;
        int destPort;
        byte[] ipHeader;
        /**TCP header in 32-bit words. 12th byte from beginning of tcp header*/
        int tcpHeaderLength;
        byte[] tcpHeader;
        byte[] data;
        byte[] sequenceNumberByte;
        int sequenceNumber;
        byte[] acknowledgmentNumberByte;
        int acknowledgmentNumber;
        byte syn;
        byte ack;
    }
    private void runProxyServerSocket() {
        mExecutorService.execute(()->{
            try {

                SocksServerSocket socksServerSocket = new SocksServerSocket("127.0.0.1",1080);
                Socket socket=socksServerSocket.accept();
                Log.v("TTT","socks client connect to socks server on port 1080");
                mExecutorService.execute(()->{

                    try {
                        byte[] buff=new byte[MAX_PACKET_SIZE];
                        int len=0;
                        while (true){
                            len = socket.getInputStream().read(buff);
                            if (len >= 0){
                                socksSelfOutputStream.write(buff,0,len);
                                socksSelfOutputStream.flush();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                mExecutorService.execute(()->{
                    try {
                        byte[] buff=new byte[MAX_PACKET_SIZE];
                        int len=0;
                        while (true){

                            len = socksSelfInputStream.read(buff);
                            if (len >= 0){
                                socket.getOutputStream().write(buff,0,len);
                                socket.getOutputStream().flush();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void makeForeground(){
        NotificationChannelCompat.Builder channelBuilder = new NotificationChannelCompat
                .Builder("my_vpn_channel", NotificationManagerCompat.IMPORTANCE_DEFAULT);
        NotificationChannelCompat channelCompat = channelBuilder.setName("my vpn notification")
                .setDescription("my vpn notification")
                .build();
        NotificationManagerCompat managerCompat= NotificationManagerCompat.from(getApplicationContext());
        managerCompat.createNotificationChannel(channelCompat);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),"my_vpn_channel");
        builder.setSmallIcon(R.drawable.ic_launcher_foreground);
        builder.setContentText("my vpn")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentText("running")
                .setContentTitle("My vpn");
        Notification notification = builder.build();
        startForeground(50,notification);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private String getKnownHostPath(Context context) {
        File file = context.getExternalFilesDir("Files");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getPath();
    }
}
