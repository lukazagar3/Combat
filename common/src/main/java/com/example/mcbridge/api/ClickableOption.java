package com.example.mcbridge.api;

public class ClickableOption {
    private final String label;
    private final String command;
    private final String tooltip;

    public ClickableOption(String label, String command, String tooltip) {
        this.label = label;
        this.command = command;
        this.tooltip = tooltip;
    }

    public String getLabel() {
        return label;
    }

    public String getCommand() {
        return command;
    }

    public String getTooltip() {
        return tooltip;
    }
}
