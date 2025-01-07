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
import java.util.HashMap;
import java.util.Map;

/**
 * A class that allows user to select visible columns of a JTable using a popup menu.
 *
 * @author Sergey A. Tachenov
 */
class JTableColumnSelector {

    private final Map<Integer, TableColumn> hiddenColumns = new HashMap<>();
    private JTable table;

    /**
     * Constructor. Call {@link #install(JTable) install} to actually
     * install it on a JTable.
     */
    public JTableColumnSelector() {
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
        final String columnName = String.format("  %s", model.getColumnName(modelIndex));
        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(columnName);
        menuItem.setSelected(true);
        menuItem.addActionListener(action -> {
            setColumnVisible(modelIndex, menuItem.isSelected());
        });
        menuItem.setPreferredSize(new Dimension(menuItem.getPreferredSize().width, 30));
        if (modelIndex == 0 || modelIndex == 2) {
            menuItem.setEnabled(false);
        }
        return menuItem;
    }

    private void setColumnVisible(int modelIndex, boolean visible) {
        if (visible)
            showColumn(modelIndex);
        else
            hideColumn(modelIndex);
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

}