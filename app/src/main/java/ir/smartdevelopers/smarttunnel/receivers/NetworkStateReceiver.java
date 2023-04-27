package ir.smartdevelopers.smarttunnel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Objects;

public class NetworkStateReceiver extends BroadcastReceiver {
    private Callback mCallback;
    private NetworkInfo lastConnectedNetwork;
    public NetworkStateReceiver(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        }
    }

    public void networkStateChange(Context context) {
        NetworkInfo networkInfo = getCurrentNetworkInfo(context);
        if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            boolean sameNetwork;
            sameNetwork = lastConnectedNetwork != null
                    && lastConnectedNetwork.getType() == networkInfo.getType()
                    && Objects.equals(lastConnectedNetwork.getExtraInfo(), networkInfo.getExtraInfo());

            mCallback.onNetworkConnected(!sameNetwork);
            lastConnectedNetwork = networkInfo;
        }else {
            mCallback.onNetworkDisconnected();
        }

    }

    private NetworkInfo getCurrentNetworkInfo(Context context) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return conn.getActiveNetworkInfo();
    }
    public  interface Callback{
        void onNetworkDisconnected();
        void onNetworkConnected(boolean changed);
    }
}
