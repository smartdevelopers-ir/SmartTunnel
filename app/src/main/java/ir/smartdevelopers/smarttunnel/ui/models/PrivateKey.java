package ir.smartdevelopers.smarttunnel.ui.models;

public class PrivateKey {
    public String key;
    public String keyType;
    public String name;

    public PrivateKey(String key, String keyType, String name) {
        this.key = key;
        this.keyType = keyType;
        this.name = name;
    }
}
