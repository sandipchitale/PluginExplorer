package dev.sandipchitale.pluginexplorer;

import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class PluginsExplorerToolWindow extends SimpleToolWindowPanel {
    private final Project project;
    private final DefaultTableModel pluginsTableModel;
    private final JBTable pluginsTable;

    private static final int DESCRIPTOR_COLUMN = -1;
    private static int index = 0;
    private static final int NAME_COLUMN = index++;
    private static final int OPEN_ON_MARKETPLACE_COLUMN = index++;
    private static final int ID_COLUMN = index++;
    private static final int VERSION_COLUMN = index++;
    private static final int ENABLED_COLUMN = index++;
    private static final int VENDOR_COLUMN = index++;
    //    private static final int SINCE_COLUMN = index++;
//    private static final int UNTIL_COLUMN = index++;
    private static final int OPEN_PATH_COLUMN = index++;
    private static final int PATH_COLUMN = index++;

    private static String[] COLUMNS = new String[index];

    static {
        COLUMNS[NAME_COLUMN] = "Name";
        COLUMNS[ID_COLUMN] = "Id";
        COLUMNS[OPEN_ON_MARKETPLACE_COLUMN] = "";
        COLUMNS[VERSION_COLUMN] = "Version";
        COLUMNS[ENABLED_COLUMN] = "";
        COLUMNS[VENDOR_COLUMN] = "Vendor";
//        COLUMNS[SINCE_COLUMN] = "Since";
//        COLUMNS[UNTIL_COLUMN] = "Until";
        COLUMNS[OPEN_PATH_COLUMN] = "";
        COLUMNS[PATH_COLUMN] = "Path";
    }

    private record PluginRecord(String value, String url, String organization, String target) {
    }

    ;

    Map<String, PluginRecord> pluginToPluginRecordMap = new HashMap<>();

    public PluginsExplorerToolWindow(@NotNull Project project) {
        super(true, true);
        this.project = project;
        pluginsTableModel = new DefaultTableModel(COLUMNS, 0) {

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == DESCRIPTOR_COLUMN) return IdeaPluginDescriptor.class;
                if (columnIndex == OPEN_ON_MARKETPLACE_COLUMN || columnIndex == ENABLED_COLUMN || columnIndex == OPEN_PATH_COLUMN)
                    return Icon.class;
                return String.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                Vector rowVector = dataVector.get(row);
                IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) rowVector.get(0);
                if (column == DESCRIPTOR_COLUMN) return ideaPluginDescriptor;
                if (column == NAME_COLUMN) return ideaPluginDescriptor.getName();
                if (column == ID_COLUMN) return ideaPluginDescriptor.getPluginId().getIdString();
                if (column == OPEN_ON_MARKETPLACE_COLUMN) return PluginsExplorerIcons.jetbrainsMarketplaceLogoIcon;
                if (column == VERSION_COLUMN) return ideaPluginDescriptor.getVersion();
                if (column == ENABLED_COLUMN)
                    return (ideaPluginDescriptor.isEnabled() ? AllIcons.Actions.Lightning : AllIcons.Actions.Suspend);
                if (column == VENDOR_COLUMN) return ideaPluginDescriptor.getVendor();
//                if (column == SINCE_COLUMN) return ideaPluginDescriptor.getSinceBuild();
//                if (column == UNTIL_COLUMN) return ideaPluginDescriptor.getUntilBuild();
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
                IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(row, DESCRIPTOR_COLUMN);
                if (column == NAME_COLUMN) {
                    String description = ideaPluginDescriptor.getDescription();
                    if (description != null) return description;
                } else if (column == OPEN_ON_MARKETPLACE_COLUMN) {
                    return "Alt double-click to open Plugin page on JetBrains Marketplace";
                } else if (column == ENABLED_COLUMN) {
                    return ideaPluginDescriptor.isEnabled() ? "Enabled" : "Disabled";
                } else if (column == OPEN_PATH_COLUMN) {
                    return "Alt double-click to open Plugin Path";
                }
                return super.getToolTipText(event);
            }
        };

        pluginsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.isAltDown() && mouseEvent.getClickCount() == 2) {
                    Point p = mouseEvent.getPoint();
                    int column = pluginsTable.columnAtPoint(p);
                    if (column == OPEN_ON_MARKETPLACE_COLUMN || column == OPEN_PATH_COLUMN) {
                        Desktop desktop = Desktop.getDesktop();
                        int row = pluginsTable.rowAtPoint(p);
                        IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) pluginsTableModel.getValueAt(row, DESCRIPTOR_COLUMN);
                        if (column == OPEN_ON_MARKETPLACE_COLUMN) {
                            String pluginName = ideaPluginDescriptor.getName();
                            PluginRecord pluginRecord = pluginToPluginRecordMap.computeIfAbsent(pluginName, (String n) -> {
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
                                    Gson gson = new Gson();
                                    PluginRecord[] pluginRecords = gson.fromJson(response.body(), PluginRecord[].class);

                                    for (PluginRecord pr : pluginRecords) {
                                        if (pluginName.equals(pr.value())) {
                                            return pr;
                                        }
                                    }
                                    // https://plugins.jetbrains.com/plugin/22006-start-spring-boot-project
                                    // desktop.browse(uri);
                                } catch (IOException | InterruptedException ignore) {
                                }
                                return null;
                            });
                            if (pluginRecord != null) {
                                try {
                                    URI uri = URI.create(
                                            String.format("https://plugins.jetbrains.com%s", pluginRecord.url()));
                                    desktop.browse(uri);
                                } catch (IOException ignore) {
                                }
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

        column = this.pluginsTable.getColumnModel().getColumn(ID_COLUMN);
//        column.setMinWidth(250);
        column.setWidth(250);
//        column.setMaxWidth(250);

        column = this.pluginsTable.getColumnModel().getColumn(VERSION_COLUMN);
        column.setMinWidth(180);
        column.setWidth(180);
        column.setMaxWidth(180);

        column = this.pluginsTable.getColumnModel().getColumn(ENABLED_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);

        column = this.pluginsTable.getColumnModel().getColumn(VENDOR_COLUMN);
        column.setMinWidth(180);
        column.setWidth(180);
        column.setMaxWidth(180);

        column = this.pluginsTable.getColumnModel().getColumn(OPEN_PATH_COLUMN);
        column.setMinWidth(40);
        column.setWidth(40);
        column.setMaxWidth(40);

        setContent(ScrollPaneFactory.createScrollPane(pluginsTable));

        final ActionManager actionManager = ActionManager.getInstance();
        ToolWindowEx pluginsExplorer = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Plugins Explorer");
        RefreshPluginsExplorerAction refreshPluginsExplorerAction = (RefreshPluginsExplorerAction) actionManager.getAction("RefreshPluginsExplorer");
        refreshPluginsExplorerAction.setPluginsExplorerToolWindow(this);
        Objects.requireNonNull(pluginsExplorer).setTitleActions(java.util.List.of(refreshPluginsExplorerAction));

        refresh();
    }

    void refresh() {
        pluginsTableModel.setRowCount(0);
        IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
        Arrays.sort(plugins, Comparator.comparing(IdeaPluginDescriptor::getName));
        for (IdeaPluginDescriptor plugin : plugins) {
            String idString = plugin.getPluginId().getIdString();
            if (!idString.isEmpty()) {
                pluginsTableModel.addRow(new IdeaPluginDescriptor[]{plugin});
            }
        }
    }
}
