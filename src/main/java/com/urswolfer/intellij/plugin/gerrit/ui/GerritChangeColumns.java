/*
 * Copyright 2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.intellij.plugin.gerrit.ui;

import static com.intellij.icons.AllIcons.Actions.Cancel;
import static com.intellij.icons.AllIcons.Actions.Checked;
import static com.intellij.icons.AllIcons.Actions.MoveDown;
import static com.intellij.icons.AllIcons.Actions.MoveUp;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.ui.avatar.AvatarIcons;
import git4idea.GitUtil;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The columns of the list of changes, and which of them the user shows.
 *
 * @author Urs Wolfer
 */
public final class GerritChangeColumns {
    /** The columns are a setting of the IDE, so the lists of all open projects follow a toggle in one of them. */
    public static final Topic<Runnable> CHANGED = Topic.create("Gerrit list columns", Runnable.class);

    private static final int AVATAR_SIZE = 16;
    private static final int AVATAR_GAP = 4;

    private final Project project;
    private final JTable table;
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final GerritSelectRevisionInfoColumn selectRevisionInfoColumn;
    private final AvatarIcons avatarIcons;
    private List<ColumnToggle> toggles = Collections.emptyList();

    /**
     * @param table what the columns are measured for, and repainted when an avatar arrives
     */
    GerritChangeColumns(Project project, JTable table) {
        this.project = project;
        this.table = table;
        this.selectRevisionInfoColumn = new GerritSelectRevisionInfoColumn(project);
        this.avatarIcons = new AvatarIcons(table, AVATAR_SIZE);
    }

    /** The columns which can be shown or hidden, in their order in the table. */
    List<ColumnToggle> getToggles() {
        return toggles;
    }

    /**
     * The columns to show for the changes, sized to them. Also replaces the toggles of the columns.
     */
    @NotNull
    ColumnInfo[] create(@NotNull List<ChangeInfo> changes) {
        ItemAndWidth number = new ItemAndWidth("", 0);
        ItemAndWidth hash = new ItemAndWidth("", 0);
        ItemAndWidth topic = new ItemAndWidth("", 0);
        ItemAndWidth subject = new ItemAndWidth("", 0);
        ItemAndWidth status = new ItemAndWidth("", 0);
        ItemAndWidth author = new ItemAndWidth("", 0);
        ItemAndWidth projectName = new ItemAndWidth("", 0);
        ItemAndWidth branch = new ItemAndWidth("", 0);
        ItemAndWidth time = new ItemAndWidth("", 0);
        Set<String> availableLabels = new TreeSet<>();
        for (ChangeInfo change : changes) {
            number = getMax(number, getNumber(change));
            hash = getMax(hash, getHash(change));
            topic = getMax(topic, getTopic(change));
            subject = getMax(subject, getShortenedSubject(change));
            status = getMax(status, getStatus(change));
            author = getMax(author, getOwner(change));
            projectName = getMax(projectName, getProject(change));
            branch = getMax(branch, getBranch(change));
            time = getMax(time, getTime(change));
            if (change.labels != null) {
                for (String label : change.labels.keySet()) {
                    availableLabels.add(label);
                }
            }
        }

        Columns columns = new Columns();
        columns.add(new GerritChangeColumnStarredInfo(), "Starred", GerritBundle.message("column.starred"));
        columns.add(
            new GerritChangeColumnInfo("#", number.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getNumber(change);
                }
            },
            GerritBundle.message("column.number"), gerritSettings.getShowChangeNumberColumn(), gerritSettings::setShowChangeNumberColumn
        );
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.id.header"), hash.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getHash(change);
                }
            },
            GerritBundle.message("column.id"), gerritSettings.getShowChangeIdColumn(), gerritSettings::setShowChangeIdColumn
        );
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.topic"), topic.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getTopic(change);
                }
            },
            GerritBundle.message("column.topic"), gerritSettings.getShowTopicColumn(), gerritSettings::setShowTopicColumn
        );

        // never hidden, so that the table always has a column
        columns.shown.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.subject"), subject.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return change.subject;
                }

                @Nullable
                @Override
                public String getPreferredStringValue() {
                    return super.getMaxStringValue();
                }

                @Override
                public String getMaxStringValue() {
                    return null; // allow to use remaining space
                }
            }
        );
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.status"), status.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getStatus(change);
                }
            },
            "Status", GerritBundle.message("column.status")
        );
        // not for a Gerrit which sends none: the column would keep room for icons which never come
        boolean showAvatars = gerritSettings.getShowAvatars()
            && changes.stream().anyMatch(change -> AvatarIcons.hasAvatar(change.owner));
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.owner"), author.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getOwner(change);
                }

                @Override
                public int getAdditionalWidth() {
                    return super.getAdditionalWidth() + (showAvatars ? JBUI.scale(AVATAR_SIZE + AVATAR_GAP) : 0);
                }

                @Nullable
                @Override
                public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
                    return new DefaultTableCellRenderer() {
                        @Override
                        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                            JLabel labelComponent = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                            labelComponent.setToolTipText(getAccountTooltip(changeInfo.owner));
                            Icon avatar = showAvatars ? avatarIcons.getIcon(changeInfo.owner) : null;
                            // an owner without one keeps the room, so that the names stay in line
                            labelComponent.setIcon(showAvatars && avatar == null ? EmptyIcon.create(AVATAR_SIZE) : avatar);
                            labelComponent.setIconTextGap(JBUI.scale(AVATAR_GAP));
                            return labelComponent;
                        }
                    };
                }
            },
            "Owner", GerritBundle.message("column.owner")
        );
        ShowProjectColumn showProjectColumn = gerritSettings.getShowProjectColumn();
        boolean listAllChanges = gerritSettings.getListAllChanges();
        // until it is shown or hidden once, it is shown where changes of more than one project are likely
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.project"), projectName.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getProject(change);
                }
            },
            GerritBundle.message("column.project"),
            showProjectColumn == ShowProjectColumn.ALWAYS
                || (showProjectColumn == ShowProjectColumn.AUTO && (listAllChanges || hasProjectMultipleRepos())),
            visible -> gerritSettings.setShowProjectColumn(visible ? ShowProjectColumn.ALWAYS : ShowProjectColumn.NEVER)
        );
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.branch"), branch.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getBranch(change);
                }
            },
            "Branch", GerritBundle.message("column.branch")
        );
        columns.add(
            new GerritChangeColumnInfo(GerritBundle.message("column.updated"), time.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getTime(change);
                }
            },
            "Updated", GerritBundle.message("column.updated")
        );
        for (final String label : availableLabels) {
            columns.add(
                new GerritChangeColumnIconLabelInfo(getShortLabelDisplay(label), label) {
                    @Override
                    public LabelInfo getLabelInfo(ChangeInfo change) {
                        return getLabel(change, label);
                    }
                },
                "Label " + label, label
            );
        }
        // never hidden: a patch set chosen in it would stay selected with nothing showing which
        columns.shown.add(selectRevisionInfoColumn);

        toggles = Collections.unmodifiableList(columns.toggles);
        return columns.shown.toArray(new ColumnInfo[0]);
    }

    private final class Columns {
        private final List<ColumnInfo> shown = new ArrayList<>();
        private final List<ColumnToggle> toggles = new ArrayList<>();

        /**
         * @param id what the hidden column is remembered by: a label is told apart from a column of the same name,
         *           and it stays the English name, which the translated one must not replace
         */
        private void add(ColumnInfo column, String id, String name) {
            add(column, name, !gerritSettings.isColumnHidden(id), visible -> gerritSettings.setColumnHidden(id, !visible));
        }

        private void add(ColumnInfo column, String name, boolean visible, Consumer<Boolean> setVisible) {
            toggles.add(new ColumnToggle(name, visible, newVisible -> {
                setVisible.consume(newVisible);
                ApplicationManager.getApplication().getMessageBus().syncPublisher(CHANGED).run();
            }));
            if (visible) {
                shown.add(column);
            }
        }
    }

    /**
     * What the header menu shows of a column. It is read while the menu is updated, possibly in the background, so
     * it holds the visibility rather than looking at the table. Newer IDEs keep the menu open after a toggle and only
     * update its items, so the toggle keeps its own state up to date as well.
     */
    public static final class ColumnToggle {
        private final String name;
        private volatile boolean visible;
        private final Consumer<Boolean> setVisible;

        private ColumnToggle(String name, boolean visible, Consumer<Boolean> setVisible) {
            this.name = name;
            this.visible = visible;
            this.setVisible = setVisible;
        }

        public String getName() {
            return name;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
            setVisible.consume(visible);
        }
    }

    private boolean hasProjectMultipleRepos() {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        return repositoryManager.getRepositories().size() > 1;
    }

    /**
     * Builds "Gerrit-like" short display of label:
     * Code-Review -> CR: collect first letter of every word part.
     */
    private String getShortLabelDisplay(String label) {
        StringBuilder result = new StringBuilder();
        for (String part : label.split("-")) {
            if (!part.isEmpty()) {
                result.append(part.charAt(0));
            }
        }
        return result.toString();
    }

    private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
        if (candidate == null) {
            return current;
        }
        int width = table.getFontMetrics(table.getFont()).stringWidth(candidate);
        if (width > current.width) {
            return new ItemAndWidth(candidate, width);
        }
        return current;
    }

    private static class ItemAndWidth {
        private final String item;
        private final int width;

        private ItemAndWidth(String item, int width) {
            this.item = item;
            this.width = width;
        }
    }

    private static String getNumber(ChangeInfo change) {
        return Integer.toString(change._number);
    }

    private static String getHash(ChangeInfo change) {
        return change.changeId.substring(0, Math.min(change.changeId.length(), 9));
    }

    private static String getTopic(ChangeInfo change) {
        return change.topic;
    }

    private static String getShortenedSubject(ChangeInfo change) {
        return change.subject.substring(0, Math.min(change.subject.length(), 80));
    }

    /**
     * A change can be work in progress and have a merge conflict at once, and the Gerrit web UI shows both.
     */
    @VisibleForTesting
    static String getStatus(ChangeInfo change) {
        if (ChangeStatus.MERGED.equals(change.status)) {
            return GerritBundle.message("status.merged");
        }
        if (ChangeStatus.ABANDONED.equals(change.status)) {
            return GerritBundle.message("status.abandoned");
        }
        List<String> status = new ArrayList<>();
        if (Boolean.TRUE.equals(change.workInProgress)) {
            status.add(GerritBundle.message("status.wip"));
        }
        if (change.mergeable != null && !change.mergeable) {
            status.add(GerritBundle.message("status.conflict"));
        }
        if (status.isEmpty() && ChangeStatus.DRAFT.equals(change.status)) {
            return GerritBundle.message("status.draft");
        }
        return String.join(", ", status);
    }

    private static String getOwner(ChangeInfo change) {
        return change.owner.name;
    }

    private static String getAccountTooltip(AccountInfo accountInfo) {
        if (accountInfo.name == null) {
            // Gerrit leaves the name out of an account which has none, as often with bots
            return accountInfo.email != null ? accountInfo.email
                : accountInfo.username != null ? accountInfo.username
                : String.valueOf(accountInfo._accountId);
        }
        if (accountInfo.email != null) {
            return String.format("%s &lt;%s&gt;", accountInfo.name, accountInfo.email);
        } else {
            return accountInfo.name;
        }
    }

    private static String getProject(ChangeInfo change) {
        return change.project;
    }

    private static String getBranch(ChangeInfo change) {
        return change.branch;
    }

    private static String getTime(ChangeInfo change) {
        return change.updated != null ? DateFormatUtil.formatPrettyDateTime(change.updated) : "";
    }

    private static LabelInfo getLabel(ChangeInfo change, String labelName) {
        Map<String,LabelInfo> labels = change.labels;
        if (labels != null) {
            return labels.get(labelName);
        } else {
            return null;
        }
    }

    private abstract static class GerritChangeColumnInfo extends ColumnInfo<ChangeInfo, String> {

        @NotNull
        private final String maxString;

        public GerritChangeColumnInfo(@NotNull String name, @NotNull String maxString) {
            super(name);
            this.maxString = maxString;
        }

        @Override
        public String getMaxStringValue() {
            return maxString;
        }

        @Override
        public int getAdditionalWidth() {
            return UIUtil.DEFAULT_HGAP;
        }
    }

    private abstract static class GerritChangeColumnIconLabelInfo extends ColumnInfo<ChangeInfo, LabelInfo> {

        private final String label;

        public GerritChangeColumnIconLabelInfo(String shortLabel, String label) {
            super(shortLabel);
            this.label = label;
        }

        @Nullable
        @Override
        public LabelInfo valueOf(ChangeInfo changeInfo) {
            return null;
        }

        public abstract LabelInfo getLabelInfo(ChangeInfo change);

        @Nullable
        @Override
        public String getTooltipText() {
            return label;
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
            return new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel labelComponent = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    LabelInfo labelInfo = getLabelInfo(changeInfo);
                    labelComponent.setIcon(getIconForLabel(labelInfo));
                    labelComponent.setToolTipText(getToolTipForLabel(labelInfo));
                    labelComponent.setHorizontalAlignment(CENTER);
                    labelComponent.setVerticalAlignment(CENTER);
                    return labelComponent;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return Checked.getIconWidth() + 20;
        }

        private static Icon getIconForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                if (labelInfo.rejected != null) {
                    return Cancel;
                }
                if (labelInfo.approved != null) {
                    return Checked;
                }
                if (labelInfo.disliked != null) {
                    return MoveDown;
                }
                if (labelInfo.recommended != null) {
                    return MoveUp;
                }
            }
            return null;
        }

        /**
         * Names the voter of the vote the icon shows: Gerrit fills in an account for each kind of vote at once.
         */
        private static String getToolTipForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                AccountInfo accountInfo = null;
                if (labelInfo.rejected != null) {
                    accountInfo = labelInfo.rejected;
                } else if (labelInfo.approved != null) {
                    accountInfo = labelInfo.approved;
                } else if (labelInfo.disliked != null) {
                    accountInfo = labelInfo.disliked;
                } else if (labelInfo.recommended != null) {
                    accountInfo = labelInfo.recommended;
                }
                if (accountInfo != null) {
                    return getAccountTooltip(accountInfo);
                }
            }
            return null;
        }
    }

    private static class GerritChangeColumnStarredInfo extends ColumnInfo<ChangeInfo, Boolean> {

        public GerritChangeColumnStarredInfo() {
            super("");
        }

        @Nullable
        @Override
        public Boolean valueOf(ChangeInfo changeInfo) {
            return null;
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
            return new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (changeInfo.starred != null && changeInfo.starred) {
                        label.setIcon(AllIcons.Nodes.Favorite);
                    }
                    label.setHorizontalAlignment(CENTER);
                    label.setVerticalAlignment(CENTER);
                    return label;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return AllIcons.Nodes.Favorite.getIconWidth();
        }
    }
}
