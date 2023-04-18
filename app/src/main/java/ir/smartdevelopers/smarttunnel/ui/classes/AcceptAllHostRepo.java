package ir.smartdevelopers.smarttunnel.ui.classes;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.UserInfo;

public class AcceptAllHostRepo implements HostKeyRepository {
    @Override
    public int check(String host, byte[] key) {
        return OK;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {

    }

    @Override
    public void remove(String host, String type) {

    }

    @Override
    public void remove(String host, String type, byte[] key) {

    }

    @Override
    public String getKnownHostsRepositoryID() {
        return "smart_known_host_id_8585";
    }

    @Override
    public HostKey[] getHostKey() {
        return new HostKey[0];
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        return new HostKey[0];
    }
}
