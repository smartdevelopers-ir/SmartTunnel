package ir.smartdevelopers.smarttunnel.ui.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.ui.fragments.DNSFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.ProxyFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.SettingsFragment;
import ir.smartdevelopers.smarttunnel.ui.interfaces.Savable;

public class SettingsActivity extends AppCompatActivity {

    private Toolbar mToolbar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mToolbar = findViewById(R.id.toolbar);
        mToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save){
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.settingsFragmentContainer);
                if ( currentFragment instanceof Savable){
                    ((Savable) currentFragment).save();
                }
            }
            return true;
        });
        goToSettingsFragment(false);
    }
    public void goToSettingsFragment(boolean animate){
        mToolbar.setTitle(R.string.settings);
        mToolbar.setNavigationOnClickListener(v->{
            onBackPressed();
        });
       showSaveMenu(false);
       FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
       if (animate){
           transaction.setCustomAnimations(R.anim.fragment_slide_right_to_left_enter,R.anim.fragment_slide_right_to_left_exit,
                   R.anim.fragment_slide_left_to_right_enter,R.anim.fragment_slide_left_to_right_exit);
       }
        transaction.replace(R.id.settingsFragmentContainer,new SettingsFragment())
                .commit();
    }

    private void showSaveMenu(boolean show) {
        MenuItem saveItem= mToolbar.getMenu().findItem(R.id.action_save);
        if (saveItem!=null){
            saveItem.setVisible(show);
        }
    }

    public void goToDNSSettingsFragment() {
        mToolbar.setTitle(R.string.dns_settins);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_slide_right_to_left_enter,R.anim.no_anim,
                        R.anim.no_anim,R.anim.fragment_slide_left_to_right_exit)
                .addToBackStack("dns_fragment")
                .replace(R.id.settingsFragmentContainer,new DNSFragment())
                .commit();
    }

    public void goToProxySettingsFragment() {
        mToolbar.setTitle(R.string.proxy_settings);
        showSaveMenu(true);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_slide_right_to_left_enter,R.anim.no_anim,
                        R.anim.no_anim,R.anim.fragment_slide_left_to_right_exit)
                .addToBackStack("proxy_fragment")
                .replace(R.id.settingsFragmentContainer,new ProxyFragment())
                .commit();
    }
}