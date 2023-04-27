package ir.smartdevelopers.smarttunnel.ui.classes;

import com.jcraft.jsch.UserInfo;

import ir.smartdevelopers.smarttunnel.utils.Logger;

public class JschSimpleUserInfo implements UserInfo {
    @Override
    public String getPassphrase() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean promptPassword(String message) {
        return false;
    }

    @Override
    public boolean promptPassphrase(String message) {
        return false;
    }

    @Override
    public boolean promptYesNo(String message) {
        return false;
    }

    @Override
    public void showMessage(String message) {
        Logger.logStyledMessage("Server message :</br>"+message,"#EB89F1",true);
    }
}
