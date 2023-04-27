package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentAppsBinding;
import ir.smartdevelopers.smarttunnel.ui.adapters.AppsAdapter;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.models.AppModel;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class AppsFragment extends Fragment {

    private FragmentAppsBinding mBinding;
    private AppsAdapter mAdapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private AppsAdapter.OnSelectAppChangeListener mOnSelectAppChangeListener;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       mBinding = FragmentAppsBinding.inflate(inflater,container,false);
       return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();
        mAdapter = new AppsAdapter();
        mAdapter.setOnSelectAppChangeListener(mOnSelectAppChangeListener);
        mBinding.appsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.appsRecyclerView.addItemDecoration(new DividerItemDecoration(requireContext(),DividerItemDecoration.VERTICAL));
        mBinding.appsRecyclerView.setAdapter(mAdapter);
        enableRadios(false);
        fetchApps(new OnCompleteListener<List<AppModel>>() {
            @Override
            public void onComplete(List<AppModel> models) {
                mAdapter.setList(models);
                enableRadios(true);
                mBinding.progressGroup.setVisibility(View.GONE);
            }
        });
    }

    private void initListeners() {
        mOnSelectAppChangeListener = new AppsAdapter.OnSelectAppChangeListener() {
            @Override
            public void onAppSelectionChanged(List<AppModel> selected) {
                Set<String> selectedApps = new HashSet<>();
                for (AppModel model : selected){
                    selectedApps.add(model.getPackageName());
                }
                PrefsUtil.setSelectedApps(requireContext(),selectedApps);
            }
        };

    }

    private void enableRadios(boolean enable){
        mBinding.radSelectedAppDontUseVpn.setEnabled(enable);
        mBinding.radSelectedAppUseVpn.setEnabled(enable);
    }

    private void initViews() {
        Util.setStatusBarPaddingToView(mBinding.appbar);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (onBackPressed()){
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search){
                mBinding.searchView.setIconified(true);
                mBinding.searchViewLayout.setVisibility(View.VISIBLE);
                mBinding.searchView.setIconified(false);
            }
            return true;
        });
        mBinding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                search(newText);
                return true;
            }
            void search(String text){
                mAdapter.getFilter().filter(text);
            }
        });

        mBinding.allowingAppRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (mAdapter == null){
                    return;
                }
                if (checkedId == R.id.radSelectedAppUseVpn){
                    PrefsUtil.setAllowSelectedApps(requireContext(),true);
                }else {
                    PrefsUtil.setAllowSelectedApps(requireContext(),false);
                }
                changeForbiddenAppsSelection();
            }
        });
        boolean selectedAppsAllowedToConnect = PrefsUtil.isAllowSelectedAppsEnabled(requireContext());
        if (selectedAppsAllowedToConnect){
            mBinding.radSelectedAppUseVpn.setChecked(true);
            mBinding.radSelectedAppUseVpn.jumpDrawablesToCurrentState();
        }else {
            mBinding.radSelectedAppDontUseVpn.setChecked(true);
            mBinding.radSelectedAppDontUseVpn.jumpDrawablesToCurrentState();
        }
    }

    private void changeForbiddenAppsSelection() {
        List<AppModel> apps = mAdapter.getModelsList();
        boolean selectedAppsAllowedToConnect = PrefsUtil.isAllowSelectedAppsEnabled(requireContext());
        for (AppModel model : apps){
            if (!model.isEnabled()){ // this is forbidden app
                // if selected apps allowed to connect we must unselect forbidden apps
                model.setSelected(!selectedAppsAllowedToConnect);
            }
        }
        mAdapter.notifyDataSetChanged();

    }

    private void fetchApps(OnCompleteListener<List<AppModel>> callback) {
        PackageManager packageManager = requireContext().getPackageManager();

        List<AppModel> appModels = new ArrayList<>();
        List<String> forbiddenApps = new ArrayList<>(PrefsUtil.getForbiddenApps(requireContext()));
        List<String> selectedApps = new ArrayList<>(PrefsUtil.getSelectedApps(requireContext()));
        boolean selectedAppsAllowedToConnect = PrefsUtil.isAllowSelectedAppsEnabled(requireContext());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(()->{
            List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            mainHandler.post(()->{
                if (getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
                    mBinding.progress.setMax(apps.size());
                }
            });
            int progress = 0;
            for (ApplicationInfo info : apps){

//                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) !=0){
//                    // this is system app
//                    continue;
//                }
                boolean selected = selectedApps.contains(info.packageName);
                boolean enabled = !forbiddenApps.contains(info.packageName);

                if (selectedAppsAllowedToConnect){
                    // we must unselect forbidden apps
                    if (!enabled){
                        selected = false;
                    }
                }else {
                    if (!enabled){
                        selected = true;
                    }
                }
                AppModel model = new AppModel(
                        info.loadLabel(packageManager).toString(),
                        info.packageName,
                        selected,
                        info.loadIcon(packageManager),
                        enabled
                );
                appModels.add(model);
                progress ++ ;
                int finalProgress = progress;
                mainHandler.post(()->{
                    if (getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
                        updateProgress(finalProgress);
                    }
                });

            }
            mainHandler.post(()->{
                if (getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
                    callback.onComplete(appModels);
                }
            });
        });
    }
    private void updateProgress(int progress){
        mBinding.progress.setProgress(progress);
        mBinding.txtAppCounter.setText(String.format(Locale.getDefault(),"%d/%d",progress,
                mBinding.progress.getMax()));
    }

    private boolean onBackPressed(){
        if (mBinding.searchViewLayout.getVisibility() == View.VISIBLE){
            mBinding.searchViewLayout.setVisibility(View.INVISIBLE);
            return false;
        }
        return true;
    }
}
