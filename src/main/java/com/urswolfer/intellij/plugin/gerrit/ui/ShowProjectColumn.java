package com.urswolfer.intellij.plugin.gerrit.ui;

public enum ShowProjectColumn {
    ALWAYS("Always"),
    AUTO("Automatic (if multiple git repositories)"),
    NEVER("Never");

    ShowProjectColumn(String label) {
        this.label = label;
    }

    private String label;

    @Override
    public String toString() {
        return label;
    }
}
