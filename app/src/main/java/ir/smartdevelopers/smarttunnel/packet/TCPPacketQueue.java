package ir.smartdevelopers.smarttunnel.packet;

import java.util.Collections;
import java.util.LinkedList;

public class TCPPacketQueue  {
    private final Object peakLock = new Object();
    private final LinkedList<TCPPacketWrapper> mPackets = new LinkedList<>();

    public TCPPacketQueue() {

    }

    public void put(TCPPacketWrapper packetWrapper){
        synchronized (mPackets){
            int pos = mPackets.indexOf(packetWrapper);
            if (pos != -1 ){
                mPackets.set(pos,packetWrapper);
            }else {
                mPackets.add(packetWrapper);
            }
            Collections.sort(mPackets);
            TCPPacketWrapper wrapper = mPackets.peek();
            if (wrapper != null && wrapper.getPacket()!= null){
                synchronized (peakLock){
                    peakLock.notify();
                }
            }
        }
    }



    public TCPPacketWrapper poll() throws InterruptedException {
        TCPPacketWrapper packetWrapper;
        synchronized (mPackets){
            packetWrapper = mPackets.peek();
        }
        if (packetWrapper == null || packetWrapper.getPacket() == null){
            synchronized (peakLock){
                peakLock.wait();
            }
        }else {
            synchronized (mPackets){
                return mPackets.poll();
            }
        }

        return poll();

    }
    public TCPPacketWrapper peek(){
        TCPPacketWrapper packetWrapper;
        synchronized (mPackets){
            packetWrapper = mPackets.peek();
        }
        return packetWrapper;
    }
    public TCPPacketWrapper get(int pos){
        return mPackets.get(pos);
    }
    public int size(){
        return mPackets.size();
    }
    public boolean contains(int seqNumber){
        for (TCPPacketWrapper wr : mPackets){
            if (wr.getSequenceNumber() == seqNumber){
                return true;
            }
        }
        return false;
    }
}
