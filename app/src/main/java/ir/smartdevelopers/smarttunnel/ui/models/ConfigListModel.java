package ir.smartdevelopers.smarttunnel.ui.models;

import java.util.Objects;

public class ConfigListModel implements Comparable<ConfigListModel>{
    public static final String PREFS_NAME = "configs_list";
    public String name;
    public String configId;
    public String type;
    private transient boolean selected;
    private long timeCreated;

    public ConfigListModel(String name, String configId,boolean selected,String type) {
        this.name = name;
        this.configId = configId;
        this.selected = selected;
        this.type = type;
        timeCreated = System.currentTimeMillis();
    }
    public ConfigListModel(String name, String configId,boolean selected,String type,long timeCreated) {
        this.name = name;
        this.configId = configId;
        this.selected = selected;
        this.timeCreated = timeCreated;
        this.type = type;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigListModel that = (ConfigListModel) o;
        return Objects.equals(configId, that.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configId);
    }

    public long getTimeCreated() {
        return timeCreated;
    }

    public void setTimeCreated(long timeCreated) {
        this.timeCreated = timeCreated;
    }

    @Override
    public int compareTo(ConfigListModel model) {
        return Long.compare(this.timeCreated,model.timeCreated);
    }
}
