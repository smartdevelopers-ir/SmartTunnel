package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentLogBinding;
import ir.smartdevelopers.smarttunnel.ui.adapters.LogAdapter;
import ir.smartdevelopers.smarttunnel.ui.classes.LogLayoutManager;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class LogFragment extends Fragment {
    private FragmentLogBinding mBinding;
    private List<LogItem> mLogs;
    private LogAdapter mLogAdapter;
    private Logger.MessageListener mMessageListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentLogBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();
        mLogAdapter = new LogAdapter(mLogs);
        mBinding.logRecyclerView.setLayoutManager(new LogLayoutManager(requireContext()));
        mBinding.logRecyclerView.setItemAnimator(null);
        mBinding.logRecyclerView.addItemDecoration(new DividerItemDecoration(requireContext(),DividerItemDecoration.VERTICAL));
        mBinding.logRecyclerView.setAdapter(mLogAdapter);
        mBinding.logRecyclerView.scrollToPosition(mLogAdapter.getItemCount()-1);
    }

    private void initListeners() {
        mMessageListener = new Logger.MessageListener() {
            @Override
            public void onNewMessage(LogItem item) {
                if (mLogAdapter != null){
                    mLogAdapter.addLog(item);
                }
            }
        };
        Logger.registerMessageListener(mMessageListener);
    }

    @Override
    public void onDestroyView() {
        if (mMessageListener != null) {
            Logger.unregisterMessageListener(mMessageListener);
        }
        super.onDestroyView();
    }

    private void initViews() {
        Util.setStatusBarPaddingToView(mBinding.appbar);
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete){
                deleteLogs();
            }else if (item.getItemId() == R.id.action_share){
                shareLog();
            }
            return true;
        });
        mLogs = PrefsUtil.getLogs(requireContext());
        if (mLogs.size() == 0){
            initDeviceInfoLogs();
        }
    }

    private void initDeviceInfoLogs() {
        List<LogItem> deviceLogs = Util.getDeviceInfoLogs(requireContext());
        mLogs.addAll(deviceLogs);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void deleteLogs() {
        PrefsUtil.clearLogs(requireContext());
        mLogs.clear();
        initDeviceInfoLogs();
        if (mLogAdapter != null){
            mLogAdapter.notifyDataSetChanged();
        }
        PrefsUtil.addLog(requireContext(),mLogs.toArray(new LogItem[0]));
    }
    public void shareLog(){
        Calendar calendar = Calendar.getInstance();
        String name = String.format(Locale.ENGLISH,"%tY-%tm-%td",calendar,calendar,calendar);
        File logFolder = requireContext().getExternalFilesDir("logs");
        File logFile = new File(logFolder,name+".log");
        Uri uri = FileProvider.getUriForFile(requireContext(),requireContext().getPackageName()+".provider",
                logFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_STREAM,uri);
        shareIntent.setType("*/*");

            Intent choser = Intent.createChooser(shareIntent,"Choose app to share");
            if (choser != null){
                startActivity(choser);
            }

    }
}
