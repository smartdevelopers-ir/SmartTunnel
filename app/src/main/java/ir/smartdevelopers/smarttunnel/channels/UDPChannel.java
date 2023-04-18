package ir.smartdevelopers.smarttunnel.channels;

import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.packet.UDPClient;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class UDPChannel extends Channel{
    private static final int MAX_CLIENT_ID = 255;
    private RemoteConnection mRemoteConnection;
    private BufferedInputStream mRemoteIn;
    private OutputStream mRemoteOut;
    private final ChannelManager mChannelManager;
    private ConcurrentLinkedQueue<Packet> mPacketQueue;
    private ArrayList<UDPClient> mUDPClients;
    private int mClientIdCounter = 0;
    /**
     * if packet queue is null we must wait to new packet arrive
     */
    private Semaphore writerLock;

    private int mUdpgwPort;
    private com.jcraft.jsch.Channel mUdpChannel;
    private Socket mSocket;
    private boolean mClosed = false;
    private ReaderThread mReaderThread;
    private final Object mRemoteOutLock = new Object();
    private KeepAliveThread mKeepAliveThread;



    public UDPChannel(String id, Packet packet, RemoteConnection remoteConnection, ChannelManager channelManager, int udpgwPort) {
        super(id, packet.getTransmissionProtocol().getSourcePort()
                , packet.getTransmissionProtocol().getDestPort()
                , packet.getSourceAddress()
                , packet.getDestAddress());

        mRemoteConnection = remoteConnection;
        mChannelManager = channelManager;
        mUdpgwPort = udpgwPort;
        mUDPClients = new ArrayList<>(256);
        mPacketQueue = new ConcurrentLinkedQueue<>();
        mPacketQueue.add(packet);
        writerLock = new Semaphore(0);
        mKeepAliveThread = new KeepAliveThread(10, id, new KeepAliveThread.KeepAliveWorker() {
            @Override
            public void doWork() {
                if (mRemoteOut != null){
                    synchronized (mRemoteOutLock){
                        try {
                            mRemoteOut.write(generateUdpgwPacket(null));
                            mRemoteOut.flush();
                            mKeepAliveThread.resetTimer();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }

            @Override
            public void onTimeOut() {
                close();
            }
        });
        mKeepAliveThread.start();


    }




    @Override
    public void onNewPacket(Packet packet) {
        mPacketQueue.add(packet);
        writerLock.release();
    }

    @Override
    public void close() {
        mClosed = true;
        if (mReaderThread != null){
            mReaderThread.interrupt();
        }
        if (mSocket != null){
            try {
                mSocket.close();
            } catch (IOException ignore) {

            }
        }
        try {
            mRemoteConnection.stopLocalPortForwarding("127.0.0.1",mUdpgwPort);
        } catch (RemoteConnectionException ignore) {}
        mChannelManager.removeChannel(this);
    }

    @Override
    public void run() {

        if (!mRemoteConnection.isConnected()){
            return;
        }
        try {
            mRemoteConnection.startLocalPortForwarding("127.0.0.1",mUdpgwPort,"127.0.0.1",mUdpgwPort);
            mSocket = new Socket();
            mSocket.setTcpNoDelay(true);
            mSocket.connect(new InetSocketAddress("127.0.0.1",mUdpgwPort));
            mRemoteIn = new BufferedInputStream(mSocket.getInputStream());
            mRemoteOut = mSocket.getOutputStream();
            mReaderThread = new ReaderThread();
            mReaderThread.start();


            byte[] udpgwPacket ;
            while (true){
                if (mClosed){
                    break;
                }
                Packet packet = mPacketQueue.poll();

                if (packet == null){
                    writerLock.acquire();
                    continue;
                }
                if (packet.getData() == null){
                    continue;
                }
                udpgwPacket = generateUdpgwPacket(packet);
                if (udpgwPacket == null){
                    continue;
                }
                synchronized (mRemoteOutLock){
                    mRemoteOut.write(udpgwPacket);
                    mRemoteOut.flush();
                    mKeepAliveThread.resetTimer();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        close();
    }

    /** @param packet can be null for generate keep-alive packet*/
    private byte[] generateUdpgwPacket(@Nullable Packet packet){
        UDPClient client;
        byte newPacketFlag = 2;
        int packetId = 0;
        if (packet != null){
            client = new UDPClient(ByteUtil.getIntValue(packet.getSourcePort()),packet.getSourceAddress(),
                    ByteUtil.getIntValue(packet.getDestPort()),packet.getDestAddress());
            if (mUDPClients.contains(client)){
                newPacketFlag =0;
                packetId = mUDPClients.indexOf(client);
            }else {
                client.id = (short) new Random().nextInt(Short.MAX_VALUE);
                if (mUDPClients.size() > mClientIdCounter) {
                    mUDPClients.set(mClientIdCounter, client);
                }else {
                    mUDPClients.add(client);
                }
                packetId = mClientIdCounter;
                mClientIdCounter ++;
                if (mClientIdCounter > MAX_CLIENT_ID){
                    mClientIdCounter = 0;
                }
            }
        }else {
            newPacketFlag =1;//for keep-alive packet

        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        // if packet is null udpPacket just has length and flag bytes
        // else it has length ( 2 bytes ) + flags(3 bytes) + dest address ( 4 bytes) + dest port ( 2 bytes) + data
        // payload dose not contains length bytes
        ByteBuffer payload = ByteBuffer.allocate(packet == null ? 3 : packet.getData().length + 3 +
                packet.getDestAddress().length + packet.getDestPort().length);

        // flags
        payload.put(newPacketFlag);
        payload.put((byte) packetId);
        payload.put((byte) 0);// I don't now what is this
        if (packet != null){
            payload.put(packet.getDestAddress());
            payload.put(packet.getDestPort());
            payload.put(packet.getData());
        }

        byte[] dataLength =  getDataLength(payload.array());
        try {
            buf.write(dataLength);
            buf.write(payload.array());
            return buf.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getDataLength(byte[] data) {
        byte first = (byte) (data.length % 256);
        byte second = (byte) (data.length / 256);
        return new byte[]{first,second};
    }

    private class ReaderThread extends Thread{

        @Override
        public void run() {
            int len =0;
            byte[] buffer = new byte[Packet.MAX_SIZE];
            try {
                while (true){
                    if (mClosed){
                        break;
                    }
                    len = mRemoteIn.read(buffer);
                     if (len > 0){

                        Packet packet = createUDPPacket(Arrays.copyOfRange(buffer,0,len));
                        if (packet == null){
                            continue;
                        }
                        mChannelManager.sendToLocal(packet);
                    }else {
                         break;
                     }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            close();
        }
    }

    /** @return null if there is no client matches to id and dest port and address*/
    private Packet createUDPPacket(byte[] data) {
        byte[] payloadLengthBytes = Arrays.copyOfRange(data,0,2);
        int payloadLength = (payloadLengthBytes[1] * 256) + payloadLengthBytes[0];
        byte[] payload = Arrays.copyOfRange(data,2,payloadLength+2);
        byte[] flags = Arrays.copyOfRange(payload,0,3);
        byte[] destAddr = Arrays.copyOfRange(payload,3,3+4);
        byte[] destPort = Arrays.copyOfRange(payload,7,7+2);
        UDPClient client = mUDPClients.get(flags[1]);
        if (client == null){
            return null;
        }
        if (!Arrays.equals(client.remoteAddress,destAddr) &&
                !Arrays.equals(destPort,ByteUtil.getByteFromInt(client.remotePort,2))){
            return null;
        }
        byte[] udpData = Arrays.copyOfRange(payload,9,payload.length);
        IPV4Header header = new IPV4Header(client.remoteAddress,client.localAddress);

        header.setTimeToLive((byte) 20);
        header.setFlag((byte) 0b010);
        header.setTypeOfService((byte) 0);
        header.setFragmentOffset((short) 0);
        header.setProtocol(UDP.PROTOCOL_NUMBER);
        header.setIdentification(ByteUtil.getByteFromInt(client.id,0));
        client.id = (short) (client.id +1);

        UDP udp = new UDP(client.remotePort,client.localPort);

        return new PacketV4(header,udp,udpData);
    }

}
