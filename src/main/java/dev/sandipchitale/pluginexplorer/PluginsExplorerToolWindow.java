package dev.sandipchitale.pluginexplorer;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDependency;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class PluginsExplorerToolWindow extends SimpleToolWindowPanel {
    private static final String PLUGINS_EXPLORER_DOWNLOAD_COUNTS_SINCE = String.format("%s_%s", PluginsExplorerToolWindow.class.getName(), "DOWNLOAD_COUNTS_SINCE");
    private static final String PLUGINS_EXPLORER_DOWNLOAD_COUNTS = String.format("%s_%s", PluginsExplorerToolWindow.class.getName(), "DOWNLOAD_COUNTS");
    private static final Logger LOG = Logger.getInstance(PluginsExplorerToolWindow.class);

    private static final Pattern PLUGIN_URL_PATTERN = Pattern.compile("^/plugin/(\\d+)-.+$");

    private final Project project;

    private final DefaultTableModel pluginsTableModel;

    record PluginIdOptional(PluginId pluginId, boolean isOptional) {
    }

    Map<PluginId, Set<PluginIdOptional>> pluginDependees = new HashMap<>();

    private final JBTable pluginsTable;

    private final TableRowSorter<DefaultTableModel> pluginsTableRowSorter;
    private final JTableColumnSelector pluginsTableColumnSelector;

    private final SearchTextField pluginsSearchTextField;

    static final int DESCRIPTOR_COLUMN = -1;
    private static int index = 0;
    private static final int NAME_COLUMN = index++;
    private static final int OPEN_ON_MARKETPLACE_COLUMN = index++;
    private static final int ID_COLUMN = index++;
    private static final int VERSION_COLUMN = index++;
    private static final int SINCE_COLUMN = index++;
    private static final int UNTIL_COLUMN = index++;
    private static final int DOWNLOADS_COLUMN = index++;
    private static final int PLUGIN_XML_COLUMN = index++;
    private static final int DEPENDENCIES_COLUMN = index++;
    private static final int DEPENDEES_COLUMN = index++;
    private static final int SOURCECODE_URL_COLUMN = index++;
    private static final int BUGTRACKER_URL_COLUMN = index++;
    private static final int ENABLED_COLUMN = index++;
    private static final int CATEGORY_COLUMN = index++;
    private static final int VENDOR_COLUMN = index++;
    private static final int INFO_COLUMN = index++;
    private static final int PATH_COLUMN = index++;
    private static final int OPEN_PATH_COLUMN = index++;

    private final String[] COLUMNS = new String[index];

    {
        COLUMNS[NAME_COLUMN] = "Name";
        COLUMNS[ID_COLUMN] = "Id";
        COLUMNS[OPEN_ON_MARKETPLACE_COLUMN] = "";
        COLUMNS[VERSION_COLUMN] = "Version";
        COLUMNS[SINCE_COLUMN] = "Since";
        COLUMNS[UNTIL_COLUMN] = "Until";
        COLUMNS[DOWNLOADS_COLUMN] = "Downloads";
        COLUMNS[PLUGIN_XML_COLUMN] = "";
        COLUMNS[DEPENDENCIES_COLUMN] = "";
        COLUMNS[DEPENDEES_COLUMN] = "";
        COLUMNS[SOURCECODE_URL_COLUMN] = "";
        COLUMNS[BUGTRACKER_URL_COLUMN] = "";
        COLUMNS[ENABLED_COLUMN] = "";
        COLUMNS[CATEGORY_COLUMN] = "Category";
        COLUMNS[VENDOR_COLUMN] = "Vendor";
        COLUMNS[INFO_COLUMN] = "";
        COLUMNS[PATH_COLUMN] = "Path";
        COLUMNS[OPEN_PATH_COLUMN] = "";
    }

    private final Gson gson;

    private final Map<String, Integer> savedDownloadsMap = new HashMap<>();
    private final Map<String, Integer> currentDownloadsMap = new HashMap<>();

    private record PluginRecord(String value,
                                String url,
                                String organization,
                                String target,
                                String sourceCodeUrl,
                                String bugtrackerUrl,
                                int downloads,
                                String icon,
                                String information,
                                List<String> tags) {
    }

    Map<PluginId, PluginRecord> pluginIdToPluginRecordMap = new ConcurrentHashMap<>();

    private static class IntegerComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            if (o1 == null && o2 == null) {
                return 0;
            } else if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            int n1 = Integer.parseInt(o1.toString());
            int n2 = Integer.parseInt(o2.toString());

            return Integer.compare(n1, n2);
        }
    }


    private static class IconHeaderRenderer extends DefaultTableCellRenderer {
        private final Icon icon;

        public IconHeaderRenderer(Icon icon) {
            this.icon = icon;
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setIcon(icon);
            setText(null); // Remove text
            return this;
        }
    }

    public PluginsExplorerToolWindow(@NotNull Project project) {
        super(true, true);
        this.project = project;

        gson = new GsonBuilder().setPrettyPrinting().create();

        BorderLayoutPanel pluginsTablePanel = new BorderLayoutPanel();

        pluginsTableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == DOWNLOADS_COLUMN) {
                    return Integer.class;
                } else if (columnIndex == DESCRIPTOR_COLUMN) {
                    return IdeaPluginDescriptor.class;
                } else if (columnIndex == OPEN_ON_MARKETPLACE_COLUMN ||
                        columnIndex == PLUGIN_XML_COLUMN ||
                        columnIndex == DEPENDENCIES_COLUMN ||
                        columnIndex == DEPENDEES_COLUMN ||
                        columnIndex == SOURCECODE_URL_COLUMN ||
                        columnIndex == BUGTRACKER_URL_COLUMN ||
                        columnIndex == ENABLED_COLUMN ||
                        columnIndex == INFO_COLUMN ||
                        columnIndex == OPEN_PATH_COLUMN) {
                    return Icon.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Object getValueAt(int row, int column) {
                Vector<?> rowVector = dataVector.get(row);
                IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) rowVector.get(0);
                if (column == DESCRIPTOR_COLUMN) return ideaPluginDescriptor;
                if (column == NAME_COLUMN) return ideaPluginDescriptor.getName();
                if (column == ID_COLUMN) return ideaPluginDescriptor.getPluginId().getIdString();
                if (column == OPEN_ON_MARKETPLACE_COLUMN) return PluginsExplorerIcons.jetbrainsMarketplaceLogoIcon;
                if (column == VERSION_COLUMN) return ideaPluginDescriptor.getVersion();
                if (column == SINCE_COLUMN) return ideaPluginDescriptor.getSinceBuild();
                if (column == UNTIL_COLUMN) return ideaPluginDescriptor.getUntilBuild();
                if (column == DOWNLOADS_COLUMN) {
                    PluginRecord pluginRecord = pluginIdToPluginRecordMap.get(ideaPluginDescriptor.getPluginId());
                    if (pluginRecord != null) {
                        return pluginRecord.downloads();
                    }
                    return 0;
                }
                ;
                if (column == PLUGIN_XML_COLUMN) return AllIcons.FileTypes.Xml;
                if (column == DEPENDENCIES_COLUMN) return AllIcons.Hierarchy.Supertypes;
                if (column == DEPENDEES_COLUMN) return AllIcons.Hierarchy.Subtypes;
                if (column == SOURCECODE_URL_COLUMN) return AllIcons.Actions.PrettyPrint;
                if (column == BUGTRACKER_URL_COLUMN) return AllIcons.Actions.StartDebugger;
                if (column == ENABLED_COLUMN)
                    return (ideaPluginDescriptor.isEnabled() ? AllIcons.Actions.Lightning : AllIcons.Actions.Suspend);
                if (column == CATEGORY_COLUMN) return ideaPluginDescriptor.getDisplayCategory();
                if (column == VENDOR_COLUMN) return ideaPluginDescriptor.getVendor();
                if (column == INFO_COLUMN) return AllIcons.General.Information;
                if (column == OPEN_PATH_COLUMN) return AllIcons.Actions.MenuOpen;
                if (column == PATH_COLUMN)

                    return ideaPluginDescriptor.getPluginPath().toString().replace(System.getProperty("user.home"), "~");
                return "";
            }
        };
        pluginsTable = new JBTable(pluginsTableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                Point p = event.getPoint();
                // Locate the renderer under the event location
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                if (row == -1 || column == -1) {
                    return "";
                }
                // In case sorting was in effect, convert the row index to real model index
                row = pluginsTableRowSorter.convertRowIndexToModel(row);
                column = pluginsTableColumnSelector.convertColumnIndexToModel(column);
                IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(row, DESCRIPTOR_COLUMN);
                if (column == NAME_COLUMN) {
                    String description = ideaPluginDescriptor.getDescription();
                    if (description != null) {
                        return description;
                    }
                    return "Double-click to view Plugin description.";
                } else if (column == VERSION_COLUMN) {
                    return ideaPluginDescriptor.getChangeNotes();
//                    String sinceBuild = ideaPluginDescriptor.getSinceBuild();
//                    String untilBuild = ideaPluginDescriptor.getUntilBuild();
//                    return String.format("Since Build: %s - Until Build: %s",
//                            Objects.requireNonNullElse(sinceBuild, "N/A"),
//                            Objects.requireNonNullElse(untilBuild, "N/A"));
                } else if (column == SINCE_COLUMN) {
                    return "Since";
                } else if (column == UNTIL_COLUMN) {
                    return "Until";
                } else if (column == DOWNLOADS_COLUMN) {
                    PluginRecord pluginRecord = pluginIdToPluginRecordMap.get(ideaPluginDescriptor.getPluginId());
                    if (pluginRecord != null) {
                        int downloads = pluginRecord.downloads();
                        Integer savedDownloads = savedDownloadsMap.getOrDefault(ideaPluginDescriptor.getPluginId().getIdString(), downloads);
                        return String.format("Downloads: %d (last checkpoint: %d) %d change %s", downloads, savedDownloads, downloads - savedDownloads, since());
                    }
                    return "Downloads";
                } else if (column == PLUGIN_XML_COLUMN) {
                    return "Double-click to open plugin.xml";
                } else if (column == DEPENDENCIES_COLUMN) {
                    return "Double-click to open Plugin Dependencies";
                } else if (column == DEPENDEES_COLUMN) {
                    return "Double-click to open Plugin Dependees";
                } else if (column == OPEN_ON_MARKETPLACE_COLUMN) {
                    return "Double-click to open Plugin page on JetBrains Marketplace";
                } else if (column == SOURCECODE_URL_COLUMN) {
                    return "Double-click to open source code URI if available.";
                } else if (column == BUGTRACKER_URL_COLUMN) {
                    return "Double-click to open bug tracker URI if available.";
                } else if (column == ENABLED_COLUMN) {
                    return ideaPluginDescriptor.isEnabled() ? "Enabled" : "Disabled";
                } else if (column == CATEGORY_COLUMN) {
                    PluginRecord pluginRecord = pluginIdToPluginRecordMap.get(ideaPluginDescriptor.getPluginId());
                    if (pluginRecord != null) {
                        return String.format("Tags:<br/><br/>%s", pluginRecord.tags().toString().replace("[", "").replace("]", "").replace(", ", "<br/>"));
                    }
                } else if (column == INFO_COLUMN) {
                    return "Double-click to see raw information";
                } else if (column == PATH_COLUMN) {
                    return "Double-click to open Plugin Path";
                } else if (column == OPEN_PATH_COLUMN) {
                    return "Double-click to open Plugin Path<br/><br/>" + ideaPluginDescriptor.getPluginPath();
                }
                return super.getToolTipText(event);
            }
        };

        pluginsTableRowSorter = new TableRowSorter<>(pluginsTableModel);
        pluginsTableRowSorter.setComparator(DOWNLOADS_COLUMN, new IntegerComparator());
        pluginsTable.setRowSorter(pluginsTableRowSorter);

        BorderLayoutPanel toolbarPanel = new BorderLayoutPanel();

        pluginsSearchTextField = new SearchTextField();
        pluginsSearchTextField.setToolTipText("Filter");
        pluginsSearchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    pluginsSearchTextField.setText("");
                    search(pluginsSearchTextField, pluginsTableRowSorter);
                    return;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    search(pluginsSearchTextField, pluginsTableRowSorter);
                }
            }
        });
        toolbarPanel.addToCenter(pluginsSearchTextField);

        JButton searchButton = new JButton(AllIcons.General.Filter);
        searchButton.setToolTipText("Filter");
        searchButton.addActionListener((ActionEvent actionEvent) -> {
            search(pluginsSearchTextField, pluginsTableRowSorter);
        });
        toolbarPanel.addToRight(searchButton);

        pluginsTablePanel.addToTop(toolbarPanel);

        pluginsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    Point p = mouseEvent.getPoint();
                    int column = pluginsTable.columnAtPoint(p);
                    column = pluginsTableColumnSelector.convertColumnIndexToModel(column);
                    if (column == NAME_COLUMN ||
                            column == VERSION_COLUMN ||
                            column == OPEN_ON_MARKETPLACE_COLUMN ||
                            column == DEPENDENCIES_COLUMN ||
                            column == DEPENDEES_COLUMN ||
                            column == PLUGIN_XML_COLUMN ||
                            column == SOURCECODE_URL_COLUMN ||
                            column == BUGTRACKER_URL_COLUMN ||
                            column == INFO_COLUMN ||
                            column == OPEN_PATH_COLUMN ||
                            column == PATH_COLUMN) {
                        Desktop desktop = Desktop.getDesktop();
                        int row = pluginsTable.rowAtPoint(p);
                        // In case sorting was in effect, convert the row index to real model index
                        row = pluginsTableRowSorter.convertRowIndexToModel(row);
                        IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(row, DESCRIPTOR_COLUMN);
                        @NotNull PluginId pluginId = ideaPluginDescriptor.getPluginId();
                        PluginRecord pluginRecord = pluginIdToPluginRecordMap.get(pluginId);
                        if (column == NAME_COLUMN) {
                            String description = ideaPluginDescriptor.getDescription();
                            if (description != null) {
//                                if (pluginRecord != null && pluginRecord.icon() != null) {
//                                    description = String.format("<div><img src=\"https://downloads.marketplace.jetbrains.com%s\" width=\"80\" height=\"80\"/></div>%s", pluginRecord.icon(), description);
//                                }
                                description = String.format("<html><head></head><body>%s</body></html>", description);

                                JEditorPane editorPane = new JEditorPane("text/html", description);
                                editorPane.setEditable(false);
                                editorPane.setPreferredSize(new Dimension(700, 600));
                                editorPane.addHyperlinkListener((HyperlinkEvent hyperlinkEvent) -> {
                                    if (hyperlinkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                        URL url = hyperlinkEvent.getURL();
                                        if (url.getProtocol().startsWith("http")) {
                                            try {
                                                Desktop.getDesktop().browse(url.toURI());
                                            } catch (IOException | URISyntaxException ignore) {
                                            }
                                        }
                                    }
                                });
                                editorPane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

                                JOptionPane.showMessageDialog(WindowManager.getInstance().getFrame(project),
                                        ScrollPaneFactory.createScrollPane(editorPane),
                                        "Plugin description",
                                        JOptionPane.PLAIN_MESSAGE);
                            }
                        } else if (column == OPEN_ON_MARKETPLACE_COLUMN) {
                            if (pluginRecord != null) {
                                try {
                                    URI uri = URI.create(
                                            String.format("https://plugins.jetbrains.com%s", pluginRecord.url()));
                                    desktop.browse(uri);
                                } catch (IOException ignore) {
                                }
                            }
                        } else if (column == VERSION_COLUMN) {
                            String changeNotes = ideaPluginDescriptor.getChangeNotes();
                            if (changeNotes != null) {
                                changeNotes = String.format("<html><head></head><body>%s</body></html>", changeNotes);

                                JEditorPane editorPane = new JEditorPane("text/html", changeNotes);
                                editorPane.setEditable(false);
                                editorPane.setPreferredSize(new Dimension(700, 600));
                                editorPane.addHyperlinkListener((HyperlinkEvent hyperlinkEvent) -> {
                                    if (hyperlinkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                        URL url = hyperlinkEvent.getURL();
                                        if (url.getProtocol().startsWith("http")) {
                                            try {
                                                Desktop.getDesktop().browse(url.toURI());
                                            } catch (IOException | URISyntaxException ignore) {
                                            }
                                        }
                                    }
                                });
                                editorPane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

                                JOptionPane.showMessageDialog(WindowManager.getInstance().getFrame(project),
                                        ScrollPaneFactory.createScrollPane(editorPane),
                                        "Plugin changeNotes",
                                        JOptionPane.PLAIN_MESSAGE);
                            }
                        } else if (column == DEPENDENCIES_COLUMN) {
                            List<IdeaPluginDependency> ideaPluginDependencies = ideaPluginDescriptor.getDependencies();
                            if (ideaPluginDependencies.isEmpty()) {
                                return;
                            }
                            DefaultListModel<IdeaPluginDependency> dependencyListModel = new DefaultListModel<>();
                            dependencyListModel.addAll(ideaPluginDependencies);
                            JBList<IdeaPluginDependency> dependenciesList = new JBList<>(dependencyListModel);
                            dependenciesList.setFont(JBFont.create(new Font(Font.MONOSPACED, Font.PLAIN, 13)));
                            ColoredListCellRenderer<IdeaPluginDependency> dependenciesListCellRenderer = new ColoredListCellRenderer<>() {
                                @Override
                                protected void customizeCellRenderer(@NotNull JList list, IdeaPluginDependency ideaPluginDependency, int index, boolean selected, boolean hasFocus) {
                                    append(String.format("%-60s %s", ideaPluginDependency.getPluginId().getIdString(), (ideaPluginDependency.isOptional() ? " [Optional]" : "")));
                                }
                            };
                            dependenciesList.setCellRenderer(dependenciesListCellRenderer);

                            Runnable gotoDependency = () -> {
                                IdeaPluginDependency ideaPluginDependency = dependenciesList.getSelectedValue();
                                if (ideaPluginDependency != null) {
                                    for (int i = 0; i < pluginsTableModel.getRowCount(); i++) {
                                        IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(i, DESCRIPTOR_COLUMN);
                                        if (pluginDescriptor.getPluginId().equals(ideaPluginDependency.getPluginId())) {
                                            int viewRowIndex = pluginsTable.convertRowIndexToView(i);
                                            if (viewRowIndex != -1) {
                                                pluginsTable.setRowSelectionInterval(viewRowIndex, viewRowIndex);
                                                pluginsTable.scrollRectToVisible(pluginsTable.getCellRect(viewRowIndex, 0, true));
                                            } else {
                                                SwingUtilities.invokeLater(() -> {
                                                    Notifications.Bus.notify(new Notification("pluginsExplorerNotificationGroup",
                                                            "Plugins explorer",
                                                            String.format("Dependency %s of %smay not be visible. May have to clear the filter.",
                                                                    ideaPluginDependency.getPluginId().getIdString(),
                                                                    pluginId.getIdString()),
                                                            NotificationType.INFORMATION));
                                                });
                                            }
                                            break;
                                        }
                                    }
                                }
                            };

                            dependenciesList.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent mouseEvent) {
                                    if (mouseEvent.getClickCount() == 2 && mouseEvent.getButton() == MouseEvent.BUTTON1) {
                                        Window windowAncestor = SwingUtilities.getWindowAncestor(dependenciesList);
                                        if (windowAncestor != null) {
                                            windowAncestor.setVisible(false);
                                            gotoDependency.run();
                                        }
                                    }
                                }
                            });

                            dependenciesList.addKeyListener(new KeyAdapter() {
                                @Override
                                public void keyPressed(KeyEvent keyEvent) {
                                    if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                                        Window windowAncestor = SwingUtilities.getWindowAncestor(dependenciesList);
                                        if (windowAncestor != null) {
                                            windowAncestor.setVisible(false);
                                            gotoDependency.run();
                                        }
                                    }
                                }
                            });

                            int option = JOptionPane.showConfirmDialog(WindowManager.getInstance().getFrame(project),
                                    dependenciesList,
                                    "Go to selected dependency of " + pluginId.getIdString(),
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.PLAIN_MESSAGE);
                            if (option == JOptionPane.YES_OPTION) {
                                gotoDependency.run();
                            }
                        } else if (column == DEPENDEES_COLUMN) {
                            Set<PluginIdOptional> dependeePluginIds = pluginDependees.get(pluginId);
                            if (dependeePluginIds == null) {
                                return;
                            }
                            DefaultListModel<PluginIdOptional> dependencyListModel = new DefaultListModel<>();
                            dependencyListModel.addAll(dependeePluginIds);
                            JBList<PluginIdOptional> dependeeList = new JBList<>(dependencyListModel);
                            dependeeList.setFont(JBFont.create(new Font(Font.MONOSPACED, Font.PLAIN, 13)));
                            ColoredListCellRenderer<PluginIdOptional> dependenciesListCellRenderer = new ColoredListCellRenderer<>() {
                                @Override
                                protected void customizeCellRenderer(@NotNull JList list, PluginIdOptional pluginIdOptional, int index, boolean selected, boolean hasFocus) {
                                    append(String.format("%-60s %s", pluginIdOptional.pluginId().getIdString(), (pluginIdOptional.isOptional() ? " [Optional]" : "")));
                                }
                            };
                            dependeeList.setCellRenderer(dependenciesListCellRenderer);

                            Runnable gotoDependee = () -> {
                                PluginIdOptional dependeedPluginIdOptional = dependeeList.getSelectedValue();
                                if (dependeedPluginIdOptional != null) {
                                    for (int i = 0; i < pluginsTableModel.getRowCount(); i++) {
                                        IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(i, DESCRIPTOR_COLUMN);
                                        if (pluginDescriptor.getPluginId().equals(dependeedPluginIdOptional.pluginId())) {
                                            int viewRowIndex = pluginsTable.convertRowIndexToView(i);

                                            if (viewRowIndex != -1) {
                                                pluginsTable.setRowSelectionInterval(viewRowIndex, viewRowIndex);
                                                pluginsTable.scrollRectToVisible(pluginsTable.getCellRect(viewRowIndex, 0, true));
                                            } else {
                                                SwingUtilities.invokeLater(() -> {
                                                    Notifications.Bus.notify(new Notification("pluginsExplorerNotificationGroup",
                                                            "Plugins explorer",
                                                            String.format("Dependee %s of %s may not be visible. Clear the filter.",
                                                                    dependeedPluginIdOptional.pluginId().getIdString(),
                                                                    pluginId.getIdString()),
                                                            NotificationType.INFORMATION));
                                                });
                                            }

                                            break;
                                        }
                                    }
                                }
                            };

                            dependeeList.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent mouseEvent) {
                                    if (mouseEvent.getClickCount() == 2 && mouseEvent.getButton() == MouseEvent.BUTTON1) {
                                        Window windowAncestor = SwingUtilities.getWindowAncestor(dependeeList);
                                        if (windowAncestor != null) {
                                            windowAncestor.setVisible(false);
                                            gotoDependee.run();
                                        }
                                    }
                                }
                            });

                            dependeeList.addKeyListener(new KeyAdapter() {
                                @Override
                                public void keyPressed(KeyEvent keyEvent) {
                                    if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                                        Window windowAncestor = SwingUtilities.getWindowAncestor(dependeeList);
                                        if (windowAncestor != null) {
                                            windowAncestor.setVisible(false);
                                            gotoDependee.run();
                                        }
                                    }
                                }
                            });

                            int option = JOptionPane.showConfirmDialog(WindowManager.getInstance().getFrame(project),
                                    dependeeList,
                                    "Go to selected dependee of " + pluginId.getIdString(),
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.PLAIN_MESSAGE);
                            if (option == JOptionPane.YES_OPTION) {
                                gotoDependee.run();
                            }
                        } else if (column == PLUGIN_XML_COLUMN) {
                            File libDir = ideaPluginDescriptor.getPluginPath().resolve("lib").toFile();
                            if (libDir.exists()) {
                                // Locate plugin.xml inside the .jar file
                                Arrays.stream(Objects.requireNonNull(libDir.listFiles())).forEach(file -> {
                                    if (file.getName().endsWith(".jar")) {
                                        try (JarFile jarFile = new JarFile(file)) {
                                            ZipEntry pluginXml = jarFile.getEntry("META-INF/plugin.xml");
                                            if (pluginXml != null) {
                                                String jarUrl = JarFileSystem.PROTOCOL_PREFIX + file.getAbsolutePath() + JarFileSystem.JAR_SEPARATOR + "META-INF/plugin.xml";
                                                VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(jarUrl);
                                                if (virtualFile != null) {
                                                    // Open as read-only
                                                    FileEditor[] fileEditors = FileEditorManager.getInstance(project).openFile(virtualFile, true);
                                                    if (fileEditors.length > 0) {
                                                        fileEditors[0].getComponent().putClientProperty(PluginsExplorerToolWindow.class.getSimpleName() + ".IdeaPluginDescriptor", ideaPluginDescriptor);
                                                    }
                                                }
                                            }
                                        } catch (IOException ignore) {
                                        }
                                    }
                                });
                            }
                        } else if (column == BUGTRACKER_URL_COLUMN) {
                            if (pluginRecord != null) {
                                try {
                                    String bugtrackerURI = pluginRecord.bugtrackerUrl();
                                    if (bugtrackerURI != null && !bugtrackerURI.isEmpty()) {
                                        desktop.browse(URI.create(bugtrackerURI));
                                    }
                                } catch (IOException ignore) {
                                }
                            }
                        } else if (column == SOURCECODE_URL_COLUMN) {
                            if (pluginRecord != null) {
                                try {
                                    String sourceCodeUrl = pluginRecord.sourceCodeUrl();
                                    if (sourceCodeUrl != null && !sourceCodeUrl.isEmpty()) {
                                        desktop.browse(URI.create(sourceCodeUrl));
                                    }
                                } catch (IOException ignore) {
                                }
                            }
                        } else if (column == INFO_COLUMN) {
                            if (pluginRecord != null) {
                                JOptionPane.showMessageDialog(WindowManager.getInstance().getFrame(project),
                                        new JScrollPane(new JBTextArea(pluginRecord.information(), 20, 120)),
                                        "Raw Information",
                                        JOptionPane.INFORMATION_MESSAGE);
                            }
                        } else {
                            Path pluginPath = ideaPluginDescriptor.getPluginPath();
                            if (pluginPath != null && pluginPath.toFile().exists()) {
                                try {
                                    desktop.open(pluginPath.toFile());
                                } catch (IOException ignore) {
                                }
                            }
                        }
                    }
                }

            }
        });

        TableColumn column;

        column = this.pluginsTable.getColumnModel().getColumn(NAME_COLUMN);
//        column.setMinWidth(250);
        column.setWidth(250);
//        column.setMaxWidth(250);

        column = this.pluginsTable.getColumnModel().getColumn(OPEN_ON_MARKETPLACE_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(PluginsExplorerIcons.jetbrainsMarketplaceLogoIcon));

        column = this.pluginsTable.getColumnModel().getColumn(PLUGIN_XML_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.FileTypes.Xml));

        column = this.pluginsTable.getColumnModel().getColumn(ID_COLUMN);
//        column.setMinWidth(250);
        column.setWidth(250);
//        column.setMaxWidth(250);

        column = this.pluginsTable.getColumnModel().getColumn(VERSION_COLUMN);
        column.setMinWidth(170);
        column.setWidth(170);
        column.setMaxWidth(170);

        column = this.pluginsTable.getColumnModel().getColumn(SINCE_COLUMN);
        column.setMinWidth(150);
        column.setWidth(150);
        column.setMaxWidth(150);

        column = this.pluginsTable.getColumnModel().getColumn(UNTIL_COLUMN);
        column.setMinWidth(150);
        column.setWidth(150);
        column.setMaxWidth(150);

        column = this.pluginsTable.getColumnModel().getColumn(DOWNLOADS_COLUMN);
        column.setMinWidth(120);
        column.setWidth(120);
        column.setMaxWidth(120);

        column = this.pluginsTable.getColumnModel().getColumn(DEPENDENCIES_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.Hierarchy.Supertypes));

        column = this.pluginsTable.getColumnModel().getColumn(DEPENDEES_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.Hierarchy.Subtypes));

        column = this.pluginsTable.getColumnModel().getColumn(SOURCECODE_URL_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.Actions.PrettyPrint));


        column = this.pluginsTable.getColumnModel().getColumn(BUGTRACKER_URL_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.Actions.StartDebugger));

        column = this.pluginsTable.getColumnModel().getColumn(ENABLED_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);

        column = this.pluginsTable.getColumnModel().getColumn(CATEGORY_COLUMN);
        column.setMinWidth(200);
        column.setWidth(200);
        column.setMaxWidth(200);

        column = this.pluginsTable.getColumnModel().getColumn(VENDOR_COLUMN);
        column.setMinWidth(180);
        column.setWidth(180);
        column.setMaxWidth(180);

        column = this.pluginsTable.getColumnModel().getColumn(INFO_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.General.Information));

        column = this.pluginsTable.getColumnModel().getColumn(OPEN_PATH_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);
        column.setHeaderRenderer(new IconHeaderRenderer(AllIcons.Actions.MenuOpen));

        pluginsTableColumnSelector = new JTableColumnSelector(
                Stream.of(
                        "Name",
                        "Open on Marketplace",
                        "ID",
                        "Version",
                        "Since",
                        "Until",
                        "Downloads",
                        "Open Plugin.xml",
                        "Show Dependencies",
                        "Show Dependees",
                        "Go Sourcecode Repository",
                        "Show Bugtracker",
                        "Enabled/Disabled",
                        "Category",
                        "Vendor",
                        "Show Raw Info",
                        "Path",
                        "Open Path"
                ).toList());
        pluginsTableColumnSelector.install(pluginsTable);
        // Hide path column
        pluginsTableColumnSelector.setColumnVisible(SINCE_COLUMN, false);
        pluginsTableColumnSelector.setColumnVisible(UNTIL_COLUMN, false);
        pluginsTableColumnSelector.setColumnVisible(PATH_COLUMN, false);

        pluginsTablePanel.addToCenter(ScrollPaneFactory.createScrollPane(pluginsTable));

        setContent(pluginsTablePanel);

        final ActionManager actionManager = ActionManager.getInstance();
        ToolWindowEx pluginsExplorer = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Plugins Explorer");

        if (pluginsExplorer != null) {
            CheckpointDownloadsAction checkpointDownloadsAction = (CheckpointDownloadsAction) actionManager.getAction("CheckpointDownloads");
            checkpointDownloadsAction.setPluginsExplorerToolWindow(this);
            checkpointDownloadsAction.getTemplatePresentation().setText("Checkpoint");

            PluginsDependenciesExplorerAction pluginsDependenciesExplorerAction = (PluginsDependenciesExplorerAction) actionManager.getAction("PluginsDependenciesExplorer");
            pluginsDependenciesExplorerAction.getTemplatePresentation().setText("Explore Plugin Dependencies and Dependees...");
            pluginsDependenciesExplorerAction.setPluginsExplorerToolWindow(this);

            GotoPluginsAction gotoPluginsAction = (GotoPluginsAction) actionManager.getAction("GotoPlugins");
            gotoPluginsAction.getTemplatePresentation().setText("Go to Plugins Page in Settings...");
            gotoPluginsAction.setPluginsExplorerToolWindow(this);

            RefreshPluginsExplorerAction refreshPluginsExplorerAction = (RefreshPluginsExplorerAction) actionManager.getAction("RefreshPluginsExplorer");
            refreshPluginsExplorerAction.getTemplatePresentation().setText("Refresh");
            refreshPluginsExplorerAction.setPluginsExplorerToolWindow(this);

            pluginsExplorer.setTitleActions(List.of(checkpointDownloadsAction,
                    pluginsDependenciesExplorerAction,
                    gotoPluginsAction,
                    refreshPluginsExplorerAction));
        }

        refresh();
    }

    DefaultTableModel getPluginsTableModel() {
        return pluginsTableModel;
    }

    Map<PluginId, Set<PluginIdOptional>> getPluginDependees() {
        return pluginDependees;
    }

    void refresh() {
        savedDownloadsMap.clear();
        String downloadsMapJson = PropertiesComponent.getInstance().getValue(PLUGINS_EXPLORER_DOWNLOAD_COUNTS, "{}");
        ((Map<String, Integer>) gson.fromJson(downloadsMapJson, (new TypeToken<Map<String, Integer>>() {
        }).getType())).forEach((Object key, Object value) -> {
            savedDownloadsMap.put(String.valueOf(key), Integer.valueOf(String.valueOf(value)));
        });
        if (savedDownloadsMap.isEmpty()) {
            Notifications.Bus.notify(new Notification("pluginsExplorerNotificationGroup",
                    "Plugins explorer",
                    "No saved download counts found.",
                    NotificationType.INFORMATION));
        }

        pluginDependees.clear();

        pluginIdToPluginRecordMap.clear();
        pluginsTableModel.setRowCount(0);
        IdeaPluginDescriptor[] ideaPluginDescriptors = PluginManagerCore.getPlugins();
        Arrays.sort(ideaPluginDescriptors, Comparator.comparing(IdeaPluginDescriptor::getName));

        new Thread(() -> {
            for (int i = 0; i < 2; i++) {
                for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
                    if (i == 0 && "JetBrains".equals(ideaPluginDescriptor.getVendor()))
                        continue; // Skip JetBrains in first loop
                    if (i == 1 && (!"JetBrains".equals(ideaPluginDescriptor.getVendor())))
                        continue; // Process JetBrains in sec ond loop
                    final int row = i;
                    PluginId pluginId = ideaPluginDescriptor.getPluginId();
                    String pluginName = ideaPluginDescriptor.getName();
                    try {
                        URI uri = URI.create(
                                String.format("https://plugins.jetbrains.com/api/searchSuggest?isIDERequest=false&term=%s",
                                        URLEncoder.encode(pluginName, StandardCharsets.UTF_8)));
                        // Create an HTTP client
                        HttpClient httpClient = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(uri)
                                .GET()
                                .build();

                        // Send the request and get the response
                        HttpResponse<String> response = null;
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        // Parse the JSON response using Gson

                        PluginRecord[] pluginRecords = gson.fromJson(response.body(), PluginRecord[].class);

                        for (PluginRecord pr : pluginRecords) {
                            if (pluginName.equals(pr.value())) {
                                pluginIdToPluginRecordMap.put(pluginId, pr);
                                String url = pr.url();
                                Matcher matcher = PLUGIN_URL_PATTERN.matcher(url);
                                if (matcher.matches()) {
                                    new Thread(() -> {
                                        // https://plugins.jetbrains.com/api/plugins/22006
                                        URI pluginUri = URI.create(String.format("https://plugins.jetbrains.com/api/plugins/%s", matcher.group(1)));
                                        // Create an HTTP client
                                        HttpRequest pluginRequest = HttpRequest.newBuilder()
                                                .uri(pluginUri)
                                                .GET()
                                                .build();

                                        // Send the request and get the response
                                        try {
                                            HttpResponse<String> pluginResponse = httpClient.send(pluginRequest, HttpResponse.BodyHandlers.ofString());
                                            String body = pluginResponse.body();
                                            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
                                            if (jsonObject != null) {
                                                JsonObject urls = jsonObject.getAsJsonObject("urls");
                                                int downloads = jsonObject.getAsJsonPrimitive("downloads").getAsInt();
                                                String sourceCodeUrl = urls.getAsJsonPrimitive("sourceCodeUrl").getAsString();
                                                String bugtrackerUrl = urls.getAsJsonPrimitive("bugtrackerUrl").getAsString();
                                                JsonPrimitive iconObject = jsonObject.getAsJsonPrimitive("icon");
                                                String icon = iconObject == null ? null : iconObject.getAsString();
                                                List<String> tags = new ArrayList<>();
                                                JsonArray tagsJsonArray = jsonObject.getAsJsonArray("tags");
                                                if (tagsJsonArray != null) {
                                                    tagsJsonArray.forEach((JsonElement jsonElement) -> {
                                                        tags.add(((JsonObject) jsonElement).getAsJsonPrimitive("name").getAsString());
                                                    });
                                                }
                                                PluginRecord pluginRecord = new PluginRecord(pr.value(),
                                                        pr.url(),
                                                        pr.organization,
                                                        pr.target,
                                                        sourceCodeUrl,
                                                        bugtrackerUrl,
                                                        downloads,
                                                        icon,
                                                        gson.toJson(jsonObject),
                                                        tags
                                                );
                                                currentDownloadsMap.put(pluginId.getIdString(), downloads);
                                                pluginIdToPluginRecordMap.put(pluginId, pluginRecord);
                                                pluginsTableModel.fireTableRowsUpdated(row, row);
                                            }
                                        } catch (IOException | InterruptedException ignore) {
                                        }
                                    }, "Plugin Node details").start();

                                }
                                break;
                            }
                        }
                    } catch (IOException | InterruptedException ignore) {
                    }
                }
            }
        }, "Plugin Nodes").start();

        for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
            List<IdeaPluginDependency> ideaPluginDependencies = ideaPluginDescriptor.getDependencies();
            for (IdeaPluginDependency ideaPluginDependency : ideaPluginDependencies) {
                pluginDependees.computeIfAbsent(ideaPluginDependency.getPluginId(),
                        (PluginId pluginId) -> new HashSet<>()).add(new PluginIdOptional(ideaPluginDescriptor.getPluginId(), ideaPluginDependency.isOptional()));
            }

            String idString = ideaPluginDescriptor.getPluginId().getIdString();
            if (!idString.isEmpty()) {
                pluginsTableModel.addRow(new IdeaPluginDescriptor[]{ideaPluginDescriptor});
            }
        }

        List<String> pluginNamesList = Arrays.stream(ideaPluginDescriptors).map(IdeaPluginDescriptor::getName).toList();
        pluginsSearchTextField.setHistory(pluginNamesList);
        pluginsSearchTextField.setHistorySize(pluginNamesList.size());
    }

    private void search(SearchTextField searchTextField, TableRowSorter<DefaultTableModel> tableRowSorter) {
        String text = searchTextField.getText();
        if (text.isEmpty()) {
            tableRowSorter.setRowFilter(null);
        } else {
            tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }

    void checkpointDownloads() {
        PropertiesComponent.getInstance().setValue(PLUGINS_EXPLORER_DOWNLOAD_COUNTS, gson.toJson(currentDownloadsMap));

        LocalDateTime localDateTime = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
        String formattedDate = localDateTime.format(formatter);
        PropertiesComponent.getInstance().setValue(PLUGINS_EXPLORER_DOWNLOAD_COUNTS_SINCE, formattedDate);

        savedDownloadsMap.clear();
        savedDownloadsMap.putAll(currentDownloadsMap);
    }

    private static String since() {
        if (PropertiesComponent.getInstance().isValueSet(PLUGINS_EXPLORER_DOWNLOAD_COUNTS_SINCE)) {
            return String.format("since %s", PropertiesComponent.getInstance().getValue(PLUGINS_EXPLORER_DOWNLOAD_COUNTS_SINCE));
        } else {
            return "No Checkpoint Saved.";
        }
    }
}
