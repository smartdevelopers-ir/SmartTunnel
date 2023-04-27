package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.ui.activities.SettingsActivity;
import ir.smartdevelopers.smarttunnel.ui.models.ProxyType;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class SettingsFragment extends PreferenceFragmentCompat {
    private Preference mProxyPrefs;
    private Preference mDNSPrefs;
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings,rootKey);
        initViews();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(ContextCompat.getDrawable(requireContext(),R.drawable.list_divider));

    }

    private void initViews() {
        mProxyPrefs = findPreference(getString(R.string.key_proxy));
        mDNSPrefs = findPreference(getString(R.string.key_dns));

        mDNSPrefs.setSummary(PrefsUtil.getDNSName(requireContext()));

        mProxyPrefs.setOnPreferenceClickListener(preference -> {
            goToProxySettingsFragment();
            return true;
        });
        mDNSPrefs.setOnPreferenceClickListener(preference -> {
            goToDNSSettingsFragment();
            return true;
        });
    }

    @Override
    public void onResume() {
        checkSummaries();
        super.onResume();
    }

    private void checkSummaries() {
        int globalProxyType = PrefsUtil.getGlobalProxyType(requireContext());
        switch (globalProxyType){
            case ProxyType.TYPE_NONE:
                mProxyPrefs.setSummary(R.string.none);
                break;
            case ProxyType.TYPE_HTTP:
                mProxyPrefs.setSummary(R.string.http_https);
                break;
            case ProxyType.TYPE_SSH:
                mProxyPrefs.setSummary(R.string.ssh_proxy);
                break;
        }
        String dnsName = PrefsUtil.getDNSName(requireContext());
        mDNSPrefs.setSummary(dnsName);
    }

    private void goToDNSSettingsFragment() {
        if (getActivity() instanceof SettingsActivity){
            ((SettingsActivity) getActivity()).goToDNSSettingsFragment();
        }
    }

    private void goToProxySettingsFragment() {
        if (getActivity() instanceof SettingsActivity){
            ((SettingsActivity) getActivity()).goToProxySettingsFragment();
        }
    }


}
