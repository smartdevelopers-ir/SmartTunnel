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
    /** For sending data to others that reads from our inputStream, We must write data
     * to this output*/
    protected transient PipedOutputStream mSelfOutputStream;
    private  transient PipedOutputStream mOut;
    /** For reading data from others that write to our outputStream, We must read data
     * from this input*/
    protected transient PipedInputStream mSelfInputStream;
    private  transient PipedInputStream mIn;


    public Config(String name, String id, String type)  {
        mName = name;
        this.id = id;
        this.type = type;

    }

    @CallSuper
    public  void connect() throws ConfigException{
        try {
            if (mIn == null ){
                mSelfOutputStream = new PipedOutputStream();
                mIn = new PipedInputStream(mSelfOutputStream, Packet.MAX_SIZE);
            }
            if (mOut == null){
                mOut = new PipedOutputStream();
                mSelfInputStream = new PipedInputStream(mOut,Packet.MAX_SIZE);
            }
        } catch (IOException e) {
            throw new ConfigException(e);
        }
    };
    public abstract Socket getMainSocket();
    /** Must be called after {@link #connect()}*/
    public  InputStream getInputStream(){
        return mIn;
    }
    /** Must be called after {@link #connect()}*/
    public OutputStream getOutputStream(){
        return mOut;
    }

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
}
