package ir.smartdevelopers.smarttunnel.ui.classes;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class LocalReader extends Thread{
    private final FileInputStream mLocalInput;
    private final Config mConfig;
    final long IDLE_TIME = 50;
    final byte[] packet = new byte[Packet.MAX_SIZE];
    private final PacketManager mPacketManager;
    public LocalReader(FileInputStream localInput, Config config, PacketManager packetManager) {
        mLocalInput = localInput;
        mConfig = config;
        mPacketManager = packetManager;
    }

    @Override
    public void run() {
        int len=0;
        try{

            while (true){
                if (mConfig.isCanceled()){
                    return;
                }
                boolean idle = true;
                ByteUtil.clear(packet);
                len=mLocalInput.read(packet);
                if (len >0){
                    mPacketManager.sendToRemoteServer(Arrays.copyOfRange(packet,0,len));
                    idle=false;
                }

                if (idle){
                    Thread.sleep(IDLE_TIME);
                }
            }
        } catch (IOException | InterruptedException e) {
            mConfig.retry();
        }
    }


}