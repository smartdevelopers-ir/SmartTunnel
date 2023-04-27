package ir.smartdevelopers.smarttunnel.ui.activities;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsCompat;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.SmartTunnelApp;
import ir.smartdevelopers.smarttunnel.ui.fragments.AppsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.ConfigsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.HomeFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.LogFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView mBottomNavigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBottomNavigationView = findViewById(R.id.bottomNavigation);
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                WindowInsetsCompat compat = new WindowInsetsCompat(WindowInsetsCompat.toWindowInsetsCompat(insets));
                SmartTunnelApp.mStatusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                return insets;
            }
        });
        if (getIntent().getData() != null){
            Bundle args = new Bundle();
            args.putParcelable(ConfigsFragment.KEY_CONFIG_URI,getIntent().getData());
            goToConfigFragment(false,args);
        }

        mBottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(mBottomNavigationView.getSelectedItemId() == item.getItemId()){
                    return false;
                }
                if (item.getItemId() == R.id.action_home){
                    goToHomeFragment(true,null);
                }else if (item.getItemId() == R.id.action_config){
                    goToConfigFragment(true,null);
                } else if (item.getItemId() == R.id.action_log) {
                    goToLogFragment();
                } else if (item.getItemId() == R.id.action_apps) {
                    goToAppsFragment();
                }
                return true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!(getSupportFragmentManager().findFragmentById(R.id.mainFragmentContainer) instanceof HomeFragment)){
                    mBottomNavigationView.setSelectedItemId(R.id.action_home);
                    return;
                }
                setEnabled(false);
                onBackPressed();
            }
        });
    }


    private void goToAppsFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new AppsFragment())
                .commit();
    }

    private void goToLogFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new LogFragment())
                .commit();
    }

    private void goToConfigFragment(boolean animate,Bundle data) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,ConfigsFragment.class,data)
                .commit();
    }

    private void goToHomeFragment(boolean animate,Bundle data) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,HomeFragment.class,data)
                .commit();
    }

//    @Override
//    public void onBackPressed() {
//        if (!(getSupportFragmentManager().findFragmentById(R.id.mainFragmentContainer) instanceof HomeFragment)){
////            goToHomeFragment();
//            mBottomNavigationView.setSelectedItemId(R.id.action_home);
//            return;
//        }
//        super.onBackPressed();
//    }
}