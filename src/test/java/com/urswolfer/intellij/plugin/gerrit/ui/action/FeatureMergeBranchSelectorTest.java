/*
 * Copyright 2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboBoxCompositeEditor;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.Arrays;

public class FeatureMergeBranchSelectorTest {
    @Test
    public void testEditorConstructionWithAssertionsAndFreeformSelection() throws Exception {
        Assert.assertTrue(ComboBoxCompositeEditor.class.desiredAssertionStatus());
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField("refs/heads/FEATURE");
            ComboBoxCompositeEditor<String, JTextField> editor =
                    FeatureMergeBranchSelector.createBranchEditor(field);
            CollectionComboBoxModel<String> model = new CollectionComboBoxModel<>();
            model.replaceAll(Arrays.asList("refs/heads/FEATURE", "refs/heads/master"));
            model.setSelectedItem(field.getText());
            JComboBox<String> combo = new JComboBox<>();
            combo.setEditable(true);
            combo.setModel(model);
            combo.setEditor(editor);
            Assert.assertEquals(field.getText(), "refs/heads/FEATURE");
            combo.setSelectedItem("refs/heads/master");
            Assert.assertEquals(field.getText(), "refs/heads/master");
            field.setText("refs/heads/manual");
            combo.setSelectedItem(field.getText());
            model.replaceAll(Arrays.asList("refs/heads/other"));
            Assert.assertEquals(field.getText(), "refs/heads/manual");
            Assert.assertEquals(editor.getItem(), "refs/heads/manual");
        });
    }
}
