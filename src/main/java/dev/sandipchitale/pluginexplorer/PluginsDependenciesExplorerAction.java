package dev.sandipchitale.pluginexplorer;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDependency;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PluginsDependenciesExplorerAction extends PluginsExplorerAbstractAction {

    private record IdeaPluginDescriptorPluginIdName(IdeaPluginDescriptor ideaPluginDescriptor, PluginId pluginId,
                                                    String name) {
    }

    private class PluginIdNameListCellRenderer extends ColoredListCellRenderer<IdeaPluginDescriptorPluginIdName> {
        private final Icon icon;

        PluginIdNameListCellRenderer(Icon icon) {
            this.icon = icon;
        }

        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends IdeaPluginDescriptorPluginIdName> list, IdeaPluginDescriptorPluginIdName pluginIdName, int index, boolean selected, boolean hasFocus) {
            setIcon(icon);
            append(String.format("%s [ %s ]", pluginIdName.name(), pluginIdName.pluginId.getIdString()));
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JPanel panel = new JPanel(new GridLayout(1, 3));

        Map<PluginId, Set<PluginsExplorerToolWindow.PluginIdOptional>> pluginDependees = pluginsExplorerToolWindow.getPluginDependees();

        BorderLayoutPanel dependenciesPanel = new BorderLayoutPanel(5, 5);
        BorderLayoutPanel dependenciesPanelToolbar = new BorderLayoutPanel();
        JLabel dependenciesLabel = new JLabel("Dependencies", AllIcons.Hierarchy.Supertypes, SwingConstants.CENTER);
        dependenciesLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        dependenciesPanelToolbar.addToCenter(dependenciesLabel);
        JButton selectDependencyInPluginsListButton = new JButton(AllIcons.Actions.ArrowExpand);
        selectDependencyInPluginsListButton.setEnabled(false);
        selectDependencyInPluginsListButton.setToolTipText("Select in Plugins List --->");
        dependenciesPanel.addToBottom(selectDependencyInPluginsListButton);
        dependenciesPanel.addToTop(dependenciesPanelToolbar);
        DefaultListModel<IdeaPluginDescriptorPluginIdName> dependenciesListModel = new DefaultListModel<>();
        JBList<IdeaPluginDescriptorPluginIdName> dependenciesList = new JBList<>(dependenciesListModel);
        dependenciesList.setFixedCellWidth(340);
        dependenciesList.setCellRenderer(new PluginIdNameListCellRenderer(AllIcons.Hierarchy.Supertypes));
        dependenciesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dependenciesPanel.addToCenter(ScrollPaneFactory.createScrollPane(dependenciesList));
        panel.add(dependenciesPanel);

        BorderLayoutPanel pluginsPanel = new BorderLayoutPanel(5, 5);

        BorderLayoutPanel pluginsPanelToolbar = new BorderLayoutPanel();
        pluginsPanelToolbar.addToLeft(new JLabel("<--- depend on "));
        pluginsPanelToolbar.addToCenter(new JLabel("| Plugins |", AllIcons.Nodes.Plugin, SwingConstants.CENTER));
        pluginsPanelToolbar.addToRight(new JLabel("<--- depend on "));
        pluginsPanel.addToTop(pluginsPanelToolbar);
        DefaultListModel<IdeaPluginDescriptorPluginIdName> pluginsListModel = new DefaultListModel<>();
        JBList<IdeaPluginDescriptorPluginIdName> pluginsList = new JBList<>(pluginsListModel);
        pluginsList.setFixedCellWidth(340);
        pluginsList.setCellRenderer(new PluginIdNameListCellRenderer(AllIcons.Nodes.Plugin));
        pluginsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pluginsPanel.addToCenter(ScrollPaneFactory.createScrollPane(pluginsList));
        panel.add(pluginsPanel);

        BorderLayoutPanel dependeesPanel = new BorderLayoutPanel(5, 5);
        BorderLayoutPanel dependeesPanelToolbar = new BorderLayoutPanel();
        dependeesPanelToolbar.addToCenter(new JLabel("Dependees", AllIcons.Hierarchy.Subtypes, SwingConstants.CENTER));
        JButton selectDependeeInPluginsListButton = new JButton(AllIcons.Actions.ArrowCollapse);
        selectDependeeInPluginsListButton.setEnabled(false);
        selectDependeeInPluginsListButton.setToolTipText("&lt;--- Select in Plugins List");
        dependeesPanel.addToBottom(selectDependeeInPluginsListButton);
        dependeesPanel.addToTop(dependeesPanelToolbar);
        DefaultListModel<IdeaPluginDescriptorPluginIdName> dependeesListModel = new DefaultListModel<>();
        JBList<IdeaPluginDescriptorPluginIdName> dependeesList = new JBList<>(dependeesListModel);
        dependeesList.setFixedCellWidth(340);
        dependeesList.setCellRenderer(new PluginIdNameListCellRenderer(AllIcons.Hierarchy.Subtypes));
        dependeesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dependeesPanel.addToCenter(ScrollPaneFactory.createScrollPane(dependeesList));
        panel.add(dependeesPanel);

        pluginsList.addListSelectionListener((ListSelectionEvent listSelectionEvent) -> {
            int selectedIndex = pluginsList.getSelectedIndex();
            dependeesListModel.clear();
            dependenciesListModel.clear();
            if (selectedIndex == -1) {
                int size = pluginsListModel.getSize();
                for (int i = 0; i < size; i++) {
                    dependeesListModel.addElement(pluginsListModel.get(i));
                    dependenciesListModel.addElement(pluginsListModel.get(i));
                }
            } else {
                IdeaPluginDescriptorPluginIdName ideaPluginDescriptorPluginIdName = pluginsListModel.get(selectedIndex);
                Set<PluginId> dependees = null;
                if (pluginDependees.get(ideaPluginDescriptorPluginIdName.pluginId()) != null) {
                    dependees = pluginDependees.get(ideaPluginDescriptorPluginIdName.pluginId()).stream().map(PluginsExplorerToolWindow.PluginIdOptional::pluginId).collect(Collectors.toSet());
                }
                List<PluginId> dependencies = ideaPluginDescriptorPluginIdName.ideaPluginDescriptor().getDependencies().stream().map(IdeaPluginDependency::getPluginId).toList();
                int size = pluginsListModel.getSize();
                for (int i = 0; i < size; i++) {
                    IdeaPluginDescriptorPluginIdName element = pluginsListModel.get(i);
                    // Filter dependees
                    if (dependees != null && dependees.contains(element.pluginId())) {
                        dependeesListModel.addElement(element);
                    }
                    // Filter dependencies
                    if (dependencies != null && dependencies.contains(element.pluginId())) {
                        dependenciesListModel.addElement(element);
                    }
                }
            }
        });

        dependenciesList.addListSelectionListener((ListSelectionEvent listSelectionEvent) -> {
            int selectedIndex = dependenciesList.getSelectedIndex();
            selectDependencyInPluginsListButton.setEnabled(selectedIndex != -1);

        });

        dependenciesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    IdeaPluginDescriptorPluginIdName ideaPluginDescriptorPluginIdName = dependenciesList.getSelectedValue();
                    int size = pluginsListModel.getSize();
                    for (int i = 0; i < size; i++) {
                        IdeaPluginDescriptorPluginIdName element = pluginsListModel.get(i);
                        if (element.pluginId().equals(ideaPluginDescriptorPluginIdName.pluginId())) {
                            pluginsList.setSelectedIndex(i);
                            final int finalI = i;
                            SwingUtilities.invokeLater(() -> {
                                pluginsList.scrollRectToVisible(pluginsList.getCellBounds(finalI, finalI));
                            });
                            break;
                        }
                    }
                }
            }
        });

        selectDependencyInPluginsListButton.addActionListener((ActionEvent actionEvent) -> {
            int selectedIndex = dependenciesList.getSelectedIndex();
            if (selectedIndex != -1) {
                IdeaPluginDescriptorPluginIdName ideaPluginDescriptorPluginIdName = dependenciesList.getSelectedValue();
                int size = pluginsListModel.getSize();
                for (int i = 0; i < size; i++) {
                    IdeaPluginDescriptorPluginIdName element = pluginsListModel.get(i);
                    if (element.pluginId().equals(ideaPluginDescriptorPluginIdName.pluginId())) {
                        pluginsList.setSelectedIndex(i);
                        final int finalI = i;
                        SwingUtilities.invokeLater(() -> {
                            pluginsList.scrollRectToVisible(pluginsList.getCellBounds(finalI, finalI));
                        });
                        break;
                    }
                }
            }
        });

        dependeesList.addListSelectionListener((ListSelectionEvent listSelectionEvent) -> {
            int selectedIndex = dependeesList.getSelectedIndex();
            selectDependeeInPluginsListButton.setEnabled(selectedIndex != -1);
        });

        dependeesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    IdeaPluginDescriptorPluginIdName ideaPluginDescriptorPluginIdName = dependeesList.getSelectedValue();
                    int size = pluginsListModel.getSize();
                    for (int i = 0; i < size; i++) {
                        IdeaPluginDescriptorPluginIdName element = pluginsListModel.get(i);
                        if (element.pluginId().equals(ideaPluginDescriptorPluginIdName.pluginId())) {
                            pluginsList.setSelectedIndex(i);
                            final int finalI = i;
                            SwingUtilities.invokeLater(() -> {
                                pluginsList.scrollRectToVisible(pluginsList.getCellBounds(finalI, finalI));
                            });
                            break;
                        }
                    }
                }
            }
        });

        selectDependeeInPluginsListButton.addActionListener((ActionEvent eactionEvent) -> {
            int selectedIndex = dependeesList.getSelectedIndex();
            if (selectedIndex != -1) {
                IdeaPluginDescriptorPluginIdName ideaPluginDescriptorPluginIdName = dependeesList.getSelectedValue();
                int size = pluginsListModel.getSize();
                for (int i = 0; i < size; i++) {
                    IdeaPluginDescriptorPluginIdName element = pluginsListModel.get(i);
                    if (element.pluginId().equals(ideaPluginDescriptorPluginIdName.pluginId())) {
                        pluginsList.setSelectedIndex(i);
                        final int finalI = i;
                        SwingUtilities.invokeLater(() -> {
                            pluginsList.scrollRectToVisible(pluginsList.getCellBounds(finalI, finalI));
                        });
                        break;
                    }
                }
            }
        });

        DefaultTableModel pluginsTableModel = pluginsExplorerToolWindow.getPluginsTableModel();
        int rowCount = pluginsTableModel.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(i, PluginsExplorerToolWindow.DESCRIPTOR_COLUMN);
            IdeaPluginDescriptorPluginIdName pluginIdName = new IdeaPluginDescriptorPluginIdName(ideaPluginDescriptor,
                    ideaPluginDescriptor.getPluginId(),
                    ideaPluginDescriptor.getName());
            dependeesListModel.add(i, pluginIdName);
            pluginsListModel.add(i, pluginIdName);
            dependenciesListModel.add(i, pluginIdName);
        }

        JOptionPane.showMessageDialog(pluginsExplorerToolWindow.getContent(), panel, "Dependees Plugin Dependencies", JOptionPane.PLAIN_MESSAGE);
    }
}
