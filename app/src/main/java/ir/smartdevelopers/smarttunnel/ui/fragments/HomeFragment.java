package ir.smartdevelopers.smarttunnel.ui.fragments;

import static android.app.Activity.RESULT_OK;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.TransitionDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
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
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.Objects;

import de.blinkt.openvpn.core.NetworkUtils;
import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentHomeBinding;
import ir.smartdevelopers.smarttunnel.ui.activities.SettingsActivity;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.utils.AlertUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding mBinding;
    private ServiceConnection mServiceConnection;
    private MyVpnService mVpnService;
    private BroadcastReceiver mStatusReceiver;
    private int currentBgId = R.id.disconnected_bg;
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
                        case MyVpnService.ACTION_DISCONNECTING:
                            onDisconnecting();
                            break;
                        case MyVpnService.ACTION_CONNECTING:
                        case MyVpnService.ACTION_RETRYING:
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
                case RETRYING:
                    onConnecting();
                    break;
                case DISCONNECTING:
                    onDisconnecting();
                    break;
                case DISCONNECTED:
                    onDisconnect();
                    break;
            }
        }
    }

    private void initViews() {
        mBinding.btnConnect.setOnClickListener(v->{
            Logger.logDebug("button clicked");
            if (isDisconnecting()){
                return;
            }
            if (isDisconnected()){
//                if (mServiceConnection != null){
//                    if (mVpnService != null){
//                        mVpnService.reset();
//                    }
//                }
                connect();
            }else {
                disconnect();
            }
        });
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings){
                openSettingsActivity();
            }
            return true;
        });

        ConfigListModel currentConfig = PrefsUtil.getSelectedConfig(requireContext());
        if (currentConfig != null && !TextUtils.isEmpty(currentConfig.note)){
            String htmlNote = currentConfig.note.replace("\n","<br />");
            CharSequence note = HtmlCompat.fromHtml(htmlNote,HtmlCompat.FROM_HTML_MODE_COMPACT);
            mBinding.txtConfigNote.setText(note);
            mBinding.noteGroup.setVisibility(View.VISIBLE);
        }else {
            mBinding.noteGroup.setVisibility(View.GONE);
        }
        Util.setStatusBarMargin(mBinding.toolbar);


    }


    private void openSettingsActivity() {
        startActivity(new Intent(requireContext(), SettingsActivity.class));
    }

    private void connect() {
        if (!NetworkUtils.isConnected(requireContext())){
            AlertUtil.showToast(requireContext(),R.string.no_connection_message,Toast.LENGTH_LONG, AlertUtil.Type.ERROR);
            return;
        }
        Intent vpnServiceIntent= VpnService.prepare(requireContext());
        if (vpnServiceIntent !=null){
            mVpnServicePermissionLauncher.launch(vpnServiceIntent);
            return;
        }
        ConfigListModel currentConfig = PrefsUtil.getSelectedConfig(requireContext());
        if (currentConfig==null){
            AlertUtil.showToast(requireContext(), R.string.selecet_config, Toast.LENGTH_SHORT, AlertUtil.Type.WARNING);
            return;
        }
        resetLogs();
        MyVpnService.connect(requireContext(),currentConfig.configId,currentConfig.type,MyVpnService.MODE_CONNECT);
        onConnecting();
    }

    private void onConnecting(){
        if (Objects.equals(mBinding.btnConnectBorder.getTag(),"connecting") ){
            return;
        }
        mBinding.btnConnectBorder.setTag("connecting");
        startConnectingAnimation();
        mBinding.txtConnectionNote.setText(R.string.connecting_);
        transitBgToDisconnected();
    }
    private void onDisconnecting(){
        if (Objects.equals(mBinding.btnConnectBorder.getTag(),"disconnecting")){
            return;
        }
        mBinding.btnConnectBorder.setTag("disconnecting");
        startConnectingAnimation();
        mBinding.txtConnectionNote.setText(R.string.disconncting);
        transitBgToDisconnected();
    }
    private void onConnect(){
        mBinding.btnConnectBorder.setTag("connected");
        mBinding.btnConnect.setBackgroundResource(R.drawable.btn_connect_active);
        mBinding.btnConnectBorder.setImageLevel(R.drawable.btn_connect_active_border);
        mBinding.txtConnectionNote.setText(R.string.connected);
        transitBgToConnected();
    }

    private void resetLogs(){
        PrefsUtil.clearLogs(requireContext());
        List<LogItem> deviceLogs = Util.getDeviceInfoLogs(requireContext());
        PrefsUtil.addLog(requireContext(),deviceLogs.toArray(new LogItem[0]));
    }
    private boolean isConnecting() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.CONNECTING;
        }
        return false;
    }
    private boolean isNetworkError() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.NETWORK_ERROR;
        }
        return false;
    }
    private boolean isDisconnected() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.DISCONNECTED;
        }
        return true;
    }
    private boolean isDisconnecting() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.DISCONNECTING;
        }
        return true;
    }
    private void disconnect() {
        Logger.logDebug("disconnect methode caled in home fragment");

        MyVpnService.disconnect(requireContext(),true);
        onDisconnecting();
    }
    private void onDisconnect(){
        if(Objects.equals("disconnected",mBinding.btnConnectBorder.getTag())){
            return;
        }
        mBinding.btnConnectBorder.setTag("disconnected");
        mBinding.btnConnect.setBackgroundResource(R.drawable.btn_connect_inactive);
        mBinding.btnConnectBorder.setImageLevel(R.drawable.btn_connect_inactive_border);
        mBinding.txtConnectionNote.setText(R.string.tap_to_connect);
        transitBgToDisconnected();
    }

    private boolean isConnected() {
        if (mVpnService!=null){
            return mVpnService.mStatus == MyVpnService.Status.CONNECTED;
        }
        return false;
    }
    private void transitBgToConnected(){
        if (currentBgId == R.id.connected_bg){
            return;
        }
        if (mBinding.getRoot().getBackground() instanceof TransitionDrawable){
            TransitionDrawable drawable = (TransitionDrawable) mBinding.getRoot().getBackground();
            drawable.setCrossFadeEnabled(true);
            drawable.startTransition(500);
            currentBgId = R.id.connected_bg;
        }
    }
    private void transitBgToDisconnected(){
        if (currentBgId == R.id.disconnected_bg){
            return;
        }
        if (mBinding.getRoot().getBackground() instanceof TransitionDrawable){
            TransitionDrawable drawable = (TransitionDrawable) mBinding.getRoot().getBackground();
            drawable.setCrossFadeEnabled(true);
            drawable.reverseTransition(500);
            currentBgId = R.id.disconnected_bg;
        }
    }
    private void startConnectingAnimation() {

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
