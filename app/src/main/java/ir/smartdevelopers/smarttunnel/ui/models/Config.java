package ir.smartdevelopers.smarttunnel.ui.models;

import androidx.annotation.CallSuper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;

import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;

public abstract class Config {
    private final String mName;
    private final String id;
    private final String type;
    private String mNote;
    private Proxy mProxy;
   protected OnPacketFromServerListener mOnPacketFromServerListener;


    public Config(String name, String id, String type)  {
        mName = name;
        this.id = id;
        this.type = type;

    }

    public abstract void connect() throws ConfigException;
    public abstract Socket getMainSocket();

    public String getName() {
        return mName;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Proxy getProxy() {
        return mProxy;
    }

    public void setProxy(Proxy proxy) {
        mProxy = proxy;
    }
    public abstract void retry();

    public abstract void cancel();
    public abstract boolean isCanceled();

    public String getNote() {
        return mNote;
    }

    public Config setNote(String note) {
        mNote = note;
        return this;
    }
    public abstract void sendPacketToRemoteServer(byte[] packet);

    public void setOnPacketFromServerListener(OnPacketFromServerListener onPacketFromServerListener) {
        mOnPacketFromServerListener = onPacketFromServerListener;
    }

    public interface OnPacketFromServerListener{
        void onPacketFromServer(byte[] packet);
    }
}
