/*
 * Copyright 2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/** An editable branch field with a dropdown and completion filtered as the user types. */
final class FeatureMergeBranchSelector extends JPanel {
    private final CollectionComboBoxModel<String> model = new CollectionComboBoxModel<>();
    private final ComboBox<String> comboBox = new ComboBox<>();
    private final TextFieldWithAutoCompletion<String> field;
    private boolean itemSetFromPopup;

    FeatureMergeBranchSelector(Project project, String initialText) {
        super(new BorderLayout());
        String text = initialText == null ? "" : initialText;
        List<String> initialItems = text.trim().isEmpty()
                ? Collections.<String>emptyList()
                : Collections.singletonList(text);
        model.replaceAll(initialItems);
        model.setSelectedItem(text);

        field = TextFieldWithAutoCompletion.create(project, model.getItems(), false, text);
        field.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                if (!itemSetFromPopup) {
                    comboBox.setPopupVisible(false);
                }
            }
        });

        ComboBoxCompositeEditor<String, TextFieldWithAutoCompletion<String>> editor =
                createBranchEditor(field);
        editor.onSetItem(new BiConsumer<String, TextFieldWithAutoCompletion<String>>() {
            @Override
            public void accept(String item, TextFieldWithAutoCompletion<String> textField) {
                itemSetFromPopup = true;
                try {
                    String value = item == null ? "" : item;
                    if (!value.equals(textField.getText())) {
                        textField.setText(value);
                    }
                } finally {
                    itemSetFromPopup = false;
                }
            }
        });

        comboBox.setEditable(true);
        comboBox.setModel(model);
        comboBox.setEditor(editor);
        comboBox.setKeySelectionManager(new JComboBox.KeySelectionManager() {
            @Override
            public int selectionForKey(char key, ComboBoxModel model) {
                return -1;
            }
        });
        comboBox.setMinimumAndPreferredWidth(JBUI.scale(400));
        add(comboBox, BorderLayout.CENTER);
    }

    static <F extends JComponent> ComboBoxCompositeEditor<String, F> createBranchEditor(F field) {
        // The platform composite editor requires at least one accessory, even with assertions enabled.
        JLabel branchIcon = new JLabel(AllIcons.Vcs.Branch);
        return ComboBoxCompositeEditor.withComponents(field, branchIcon);
    }

    String getText() {
        return field.getText();
    }

    void setText(String text) {
        field.setText(text == null ? "" : text);
    }

    void addTextChangeListener(final Runnable listener) {
        field.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                listener.run();
            }
        });
    }

    /** Replaces suggestions without changing the freeform text currently in the editor. */
    void setVariants(Collection<String> variants) {
        String currentText = getText();
        comboBox.setSelectedItem(currentText);

        List<String> sortedVariants = new ArrayList<>();
        if (variants != null) {
            for (String variant : variants) {
                if (variant != null && !variant.trim().isEmpty()) {
                    sortedVariants.add(variant);
                }
            }
        }
        Collections.sort(sortedVariants);
        model.replaceAll(sortedVariants);
        field.setVariants(model.getItems());
        field.setText(currentText);
    }
}
