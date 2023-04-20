package ir.smartdevelopers.smarttunnel.channels;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;

public abstract class RemoteConnection {


    public abstract int startLocalPortForwarding(String localAddress,int localPort,String remoteAddress,int remotePort) throws RemoteConnectionException;
    public abstract void stopLocalPortForwarding(String localAddress,int localPort) throws RemoteConnectionException;
    public abstract DirectTCPChannel startDirectTCPChannel(String localAddress,int localPort,String remoteAddress,int remotePort) throws RemoteConnectionException;
    public abstract void stopDirectTCPChannel(DirectTCPChannel channel) throws RemoteConnectionException;
    public abstract void setProxy(Proxy proxy);
    public abstract void connect() throws RemoteConnectionException;
    public abstract void disconnect();
    public abstract boolean isConnected();
    public abstract Socket getMainSocket();
    public abstract boolean isPortInUse(int port);
    public abstract static class DirectTCPChannel{
        private InputStream mRemoteIn;
        private OutputStream mRemoteOut;

        protected abstract void start() throws RemoteConnectionException;
        protected abstract void stop() throws RemoteConnectionException;
        public abstract boolean isConnected();
        /** every thing comes from remote can be read from this */
        public void setRemoteIn(InputStream remoteIn) {
            mRemoteIn = remoteIn;
        }
        /** every that must be send to remote must be write to this */
        public void setRemoteOut(OutputStream remoteOut) {
            mRemoteOut = remoteOut;
        }
        /** every thing comes from remote can be read from this */
        public InputStream getRemoteIn() {
            return mRemoteIn;
        }
        /** every that must be send to remote must be write to this */
        public OutputStream getRemoteOut() {
            return mRemoteOut;
        }
    }
}
