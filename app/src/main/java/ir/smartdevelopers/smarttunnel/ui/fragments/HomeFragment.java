package ir.smartdevelopers.smarttunnel.ui.fragments;

import static android.app.Activity.RESULT_OK;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.net.Proxy;
import java.util.Objects;

import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentHomeBinding;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding mBinding;
    private ServiceConnection mServiceConnection;
    private MyVpnService mVpnService;
    private BroadcastReceiver mStatusReceiver;
    private ActivityResultLauncher<Intent> mVpnServicePermissionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK){
                        connect();
                    }
                }
            });
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentHomeBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initReceivers();
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mVpnService =( (MyVpnService.VpnBinder) service).getVpnService();
                manageConnectButtonState();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        Intent vpnServiceIntent = new Intent(requireContext(),MyVpnService.class);
        requireContext().bindService(vpnServiceIntent,mServiceConnection,Context.BIND_AUTO_CREATE);

        initViews();
    }

    @Override
    public void onDestroyView() {
        if (mStatusReceiver!=null){
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mStatusReceiver);
        }
        super.onDestroyView();
    }

    private void initReceivers() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(requireContext());
        mStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null){
                    switch (action){
                        case MyVpnService.ACTION_DISCONNECTED:
                            onDisconnect();
                            break;
                        case MyVpnService.ACTION_CONNECTING:
                            onConnecting();
                            break;
                        case MyVpnService.ACTION_CONNECTED:
                            onConnect();
                            break;
                    }
                }
            }
        };
        IntentFilter statusReceiverFilter = new IntentFilter();
        statusReceiverFilter.addAction(MyVpnService.ACTION_DISCONNECTED);
        statusReceiverFilter.addAction(MyVpnService.ACTION_CONNECTING);
        statusReceiverFilter.addAction(MyVpnService.ACTION_CONNECTED);
        manager.registerReceiver(mStatusReceiver,statusReceiverFilter);

    }

    private void manageConnectButtonState() {
        if (mVpnService!=null){
            switch (mVpnService.mStatus){
                case CONNECTED:
                    onConnect();
                    break;
                case CONNECTING:
                case NETWORK_ERROR:
                    onConnecting();
                    break;
                case DISCONNECTED:
                    onDisconnect();
                    break;
            }
        }
    }

    private void initViews() {
        mBinding.btnConnect.setOnClickListener(v->{
            if (isConnected() || isConnecting()){
                disconnect();
            }else {
                connect();
            }
        });


    }

    private void connect() {
        Intent vpnServiceIntent= VpnService.prepare(requireContext());
        if (vpnServiceIntent !=null){
            mVpnServicePermissionLauncher.launch(vpnServiceIntent);
            return;
        }
        ConfigListModel currentConfig = PrefsUtil.getSelectedConfig(requireContext());
        if (currentConfig==null){
            Toast.makeText(requireContext(), R.string.selecet_config, Toast.LENGTH_SHORT).show();
            return;
        }
        MyVpnService.connect(requireContext(),currentConfig.configId,currentConfig.type);
        onConnecting();
    }

    private void onConnecting(){
        if (Objects.equals(mBinding.btnConnectBorder.getTag(),"connecting")){
            return;
        }
        startConnectingAnimation();
        mBinding.txtConnectionNote.setTextColor(R.string.connecting_);
    }
    private void onConnect(){
        mBinding.btnConnectBorder.setTag("connected");
        mBinding.btnConnect.setBackgroundResource(R.drawable.btn_connect_active);
        mBinding.btnConnectBorder.setImageLevel(R.drawable.btn_connect_active_border);
        mBinding.txtConnectionNote.setTextColor(R.string.connected);
    }

    private boolean isConnecting() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.CONNECTING;
        }
        return false;
    }

    private void disconnect() {
        MyVpnService.disconnect(requireContext());
    }
    private void onDisconnect(){
        mBinding.btnConnectBorder.setTag("disconnected");
        mBinding.btnConnect.setBackgroundResource(R.drawable.btn_connect_inactive);
        mBinding.btnConnectBorder.setImageLevel(R.drawable.btn_connect_inactive_border);
        mBinding.txtConnectionNote.setTextColor(R.string.tap_to_connect);
    }

    private boolean isConnected() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.CONNECTED;
        }
        return false;
    }
    private void startConnectingAnimation() {
        if (Objects.equals(mBinding.btnConnectBorder.getTag(),"connecting")){
            return;
        }
        mBinding.btnConnectBorder.setTag("connecting");
        mBinding.btnConnect.setBackgroundResource(R.drawable.btn_connect_activating);
        mBinding.btnConnectBorder.setImageResource(R.drawable.btn_connect_activating_border);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable animationRunnable = new Runnable() {
            boolean revers= false;
            final int duration = 800;
            @Override
            public void run() {
                if (!Objects.equals(mBinding.btnConnectBorder.getTag(),"connecting")){
                    return;
                }
                if (!(mBinding.btnConnectBorder.getDrawable() instanceof TransitionDrawable)){
                    return;
                }
                TransitionDrawable transition = (TransitionDrawable) mBinding.btnConnectBorder.getDrawable();
                transition.setCrossFadeEnabled(true);
                if (revers){
                    transition.reverseTransition(duration);
                }else {
                    transition.startTransition(duration);
                }
                revers = !revers;
                handler.postDelayed(this,duration);
            }
        };
        handler.post(animationRunnable);
    }


}
