/*
 * (C) Sergey A. Tachenov
 * This thing belongs to public domain. Really.
 */
package dev.sandipchitale.pluginexplorer;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class that allows user to select visible columns of a JTable using a popup menu.
 *
 * @author Sergey A. Tachenov
 */
class JTableColumnSelector {

    private final Map<Integer, TableColumn> hiddenColumns = new TreeMap<>();
    private final Map<Integer, JCheckBoxMenuItem> menuItems = new TreeMap<>();
    private JTable table;

    private final List<String> columnLabels;

    /**
     * Constructor. Call {@link #install(JTable) install} to actually
     * install it on a JTable.
     */
    public JTableColumnSelector(List<String> columnLabels) {
        this.columnLabels = columnLabels;
    }

    /**
     * Installs this selector on a given table.
     *
     * @param table the table to install this selector on
     */
    public void install(JTable table) {
        this.table = table;
        table.getTableHeader().setComponentPopupMenu(createHeaderMenu());
    }

    private JPopupMenu createHeaderMenu() {
        final JPopupMenu headerMenu = new JPopupMenu();
        final TableModel model = table.getModel();
        for (int i = 0; i < model.getColumnCount(); ++i)
            headerMenu.add(createMenuItem(i));
        return headerMenu;
    }

    private JCheckBoxMenuItem createMenuItem(final int modelIndex) {
        final TableModel model = table.getModel();
        final String columnName = String.format("  %s", columnLabels.get(modelIndex));
        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(columnName);
        menuItems.put(modelIndex, menuItem);
        menuItem.setSelected(true);
        menuItem.addActionListener(action -> {
            setColumnVisible(modelIndex, menuItem.isSelected(), false);
        });
        menuItem.setPreferredSize(new Dimension(menuItem.getPreferredSize().width, 30));
        if (modelIndex == 0 || modelIndex == 2) {
            menuItem.setEnabled(false);
        }
        return menuItem;
    }

    void setColumnVisible(int modelIndex, boolean visible) {
        setColumnVisible(modelIndex, visible, true);
    }

    private void setColumnVisible(int modelIndex, boolean visible, boolean adjustMenuState) {
        if (visible)
            showColumn(modelIndex);
        else
            hideColumn(modelIndex);
        if (adjustMenuState) {
            menuItems.get(modelIndex).setSelected(visible);
        }
    }

    private void showColumn(int modelIndex) {
        TableColumn column = hiddenColumns.remove(modelIndex);
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.addColumn(column);
        final int addedViewIndex = columnModel.getColumnCount() - 1;
        if (modelIndex < columnModel.getColumnCount())
            columnModel.moveColumn(addedViewIndex, modelIndex);
    }

    private void hideColumn(int modelIndex) {
        int vIndex = table.convertColumnIndexToView(modelIndex);
        TableColumnModel columnModel = table.getColumnModel();
        TableColumn column = columnModel.getColumn(vIndex);
        columnModel.removeColumn(column);
        hiddenColumns.put(modelIndex, column);
        workaroundForSwingIndexOutOfBoundsBug(column);
    }

    private void workaroundForSwingIndexOutOfBoundsBug(TableColumn column) {
        JTableHeader tableHeader = table.getTableHeader();
        if (tableHeader.getDraggedColumn() == column) {
            tableHeader.setDraggedColumn(null);
        }
    }

    int convertColumnIndexToModel(int column) {
        int[] columnToReturn = new int[]{column};
        hiddenColumns.forEach((Integer modelIndex, TableColumn tableColumn) -> {
            if (modelIndex <= columnToReturn[0]) {
                columnToReturn[0]++;
            }
        });
        return columnToReturn[0];
    }
}