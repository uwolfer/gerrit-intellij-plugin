/*
 * Copyright 2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import javax.swing.JTextField;
import java.util.function.Supplier;

/** Keeps an untouched merge subject aligned with the selected source branch. */
final class FeatureMergeSubjectUpdater implements Runnable {
    private final Supplier<String> sourceBranchText;
    private final JTextField subjectField;
    private String lastAutomaticSubject;
    private boolean updatingSubject;

    FeatureMergeSubjectUpdater(JTextField sourceBranchField, JTextField subjectField) {
        this(sourceBranchField::getText, subjectField);
    }

    FeatureMergeSubjectUpdater(Supplier<String> sourceBranchText, JTextField subjectField) {
        this.sourceBranchText = sourceBranchText;
        this.subjectField = subjectField;
        this.lastAutomaticSubject = subjectField.getText();
    }

    @Override
    public void run() {
        if (updatingSubject) {
            return;
        }

        String currentSubject = subjectField.getText();
        if (currentSubject.trim().isEmpty() || currentSubject.equals(lastAutomaticSubject)) {
            String automaticSubject = FeatureMergeBranchResolver.defaultSubject(sourceBranchText.get());
            lastAutomaticSubject = automaticSubject;
            if (!automaticSubject.equals(currentSubject)) {
                updatingSubject = true;
                try {
                    subjectField.setText(automaticSubject);
                } finally {
                    updatingSubject = false;
                }
            }
        }
    }
}
