package ir.smartdevelopers.smarttunnel.ui.models;

import android.os.ParcelFileDescriptor;

import com.google.gson.Gson;

import java.net.Socket;

import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;

public abstract class Config {
    private String mName;
    private String id;
    private String type;
    private String mNote;
    private String expireDate;
    private Proxy mProxy;
    protected  State mState ;
   protected transient ParcelFileDescriptor mFileDescriptor;
    protected transient MyVpnService mVpnService;

    public Config(String name, String id, String type)  {
        mName = name;
        this.id = id;
        this.type = type;

    }

    public String getExpireDate() {
        return expireDate;
    }

    public Config setExpireDate(String expireDate) {
        this.expireDate = expireDate;
        return this;
    }

    public enum State {
        CONNECTED,DISCONNECTED,DISCONNECTING,CONNECTING,RECONNECTING,WAITING_FOR_NETWORK
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

    public void setFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        mFileDescriptor = fileDescriptor;
    }

    public abstract ParcelFileDescriptor getFileDescriptor();

    public void setVpnService(MyVpnService vpnService) {
        mVpnService = vpnService;
    }
}
