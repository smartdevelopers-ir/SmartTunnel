package ir.smartdevelopers.smarttunnel.ui.models;

import android.graphics.drawable.Drawable;
import android.net.Uri;

public class AppModel {
    private String appName;
    private String packageName;
    private boolean selected;
    private Drawable icon;
    private boolean enabled;

    public AppModel(String appName, String packageName, boolean selected, Drawable icon, boolean enabled) {
        this.appName = appName;
        this.packageName = packageName;
        this.selected = selected;
        this.icon = icon;
        this.enabled = enabled;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
