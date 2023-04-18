package ir.smartdevelopers.smarttunnel.ui.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.ui.fragments.AppsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.ConfigsFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.HomeFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView mBottomNavigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBottomNavigationView = findViewById(R.id.bottomNavigation);


        mBottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_home){
                    goToHomeFragment();
                }else if (item.getItemId() == R.id.action_config){
                    goToConfigFragment();
                } else if (item.getItemId() == R.id.action_log) {
                    goToLogFragment();
                } else if (item.getItemId() == R.id.action_apps) {
                    goToAppsFragment();
                }
                return true;
            }
        });

    }


    private void goToAppsFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new AppsFragment())
                .commit();
    }

    private void goToLogFragment() {

    }

    private void goToConfigFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new ConfigsFragment())
                .commit();
    }

    private void goToHomeFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer,new HomeFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (!(getSupportFragmentManager().findFragmentById(R.id.mainFragmentContainer) instanceof HomeFragment)){
//            goToHomeFragment();
            mBottomNavigationView.setSelectedItemId(R.id.action_home);
            return;
        }
        super.onBackPressed();
    }
}