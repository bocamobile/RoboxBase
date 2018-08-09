package celtech.roboxbase.configuration;

import java.util.Optional;

/**
 *
 * @author George
 */
public class PrintProfileSetting {
    
    private String id;
    private String settingName;
    private String defaultValue;
    private String valueType;
    private String tooltip;
    private Optional<String> unit = Optional.empty();
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSettingName() {
        return settingName;
    }

    public void setSettingName(String settingName) {
        this.settingName = settingName;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }
    
    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public Optional<String> getUnit() {
        return unit;
    }

    public void setUnit(Optional<String> unit) {
        this.unit = unit;
    }
}
