package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentDnsBinding;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class DNSFragment extends Fragment {
    private FragmentDnsBinding mBinding;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentDnsBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
    }

    private void initViews() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackPressed();
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        mBinding.radGoogleDns.setOnClickListener(v->{
            mBinding.customDnsContainer.setVisibility(View.INVISIBLE);
            PrefsUtil.setDNS1(requireContext(),"8.8.8.8");
            PrefsUtil.setDNS2(requireContext(),"8.8.4.4");
            PrefsUtil.setDNSName(requireContext(),getString(R.string.google));
        });
        mBinding.radCloudflareDns.setOnClickListener(v->{
            mBinding.customDnsContainer.setVisibility(View.INVISIBLE);
            PrefsUtil.setDNS1(requireContext(),"1.1.1.1");
            PrefsUtil.setDNS2(requireContext(),"1.0.0.1");
            PrefsUtil.setDNSName(requireContext(),getString(R.string.cloudflare));
        });
        mBinding.radCustomDns.setOnClickListener(v->{
            mBinding.customDnsContainer.setVisibility(View.VISIBLE);

        });

        String dnsName = PrefsUtil.getDNSName(requireContext());
        if (Objects.equals(dnsName,getString(R.string.google))){
            mBinding.radGoogleDns.setChecked(true);
            mBinding.radGoogleDns.jumpDrawablesToCurrentState();
        } else if (Objects.equals(dnsName, getString(R.string.cloudflare))) {
            mBinding.radCloudflareDns.setChecked(true);
            mBinding.radCloudflareDns.jumpDrawablesToCurrentState();
        }else {
            mBinding.radCustomDns.setChecked(true);
            mBinding.radCustomDns.jumpDrawablesToCurrentState();
            mBinding.customDnsContainer.setVisibility(View.VISIBLE);
            String dns1 = PrefsUtil.getDNS1(requireContext());
            String dns2 = PrefsUtil.getDNS2(requireContext());
            mBinding.edtDns1.setText(dns1);
            mBinding.edtDns2.setText(dns2);
        }
    }

   public void onBackPressed(){
       if (mBinding.radCustomDns.isChecked()){
           String dns1 = mBinding.edtDns1.getText().toString();
           String[] dns1Parts = dns1.split("\\.");
           boolean customDns1 = false;
           boolean customDns2 = false;
           if (dns1Parts.length == 4){
               customDns1 = true;
               PrefsUtil.setDNS1(requireContext(),dns1);
           }
           String dns2 = mBinding.edtDns2.getText().toString();
           String[] dns2Parts = dns2.split("\\.");
           if (dns2Parts.length == 4){
               customDns2 = true;
               PrefsUtil.setDNS2(requireContext(),dns2);
           }
           if (customDns1 || customDns2){
               PrefsUtil.setDNSName(requireContext(),getString(R.string.custom));
               if (!customDns1){
                   PrefsUtil.setDNS1(requireContext(),"");
               }
               if (!customDns2){
                   PrefsUtil.setDNS2(requireContext(),"");
               }
           }
       }
   }
}
