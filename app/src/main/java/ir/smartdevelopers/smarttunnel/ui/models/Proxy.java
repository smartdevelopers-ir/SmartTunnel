package ir.smartdevelopers.smarttunnel.ui.models;

public class Proxy {
    private String mAddress;
    private int mPort;

    public Proxy(String address, int port) {
        mAddress = address;
        mPort = port;
    }

    public String getAddress() {
        return mAddress;
    }

    public Proxy setAddress(String address) {
        mAddress = address;
        return this;
    }

    public int getPort() {
        return mPort;
    }

    public Proxy setPort(int port) {
        mPort = port;
        return this;
    }
}
