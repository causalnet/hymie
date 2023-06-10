package au.net.causal.hymie.ui;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Function;

public class SimpleTableModel<R> extends AbstractTableModel
{
    private final List<Column<R, ?>> columnDefintions;
    private final List<R> rows;

    public SimpleTableModel(List<? extends Column<R, ?>> columnDefintions, List<R> rows)
    {
        this.columnDefintions = List.copyOf(columnDefintions);
        this.rows = rows;
    }

    @Override
    public int getRowCount()
    {
        return rows.size();
    }

    @Override
    public int getColumnCount()
    {
        return columnDefintions.size();
    }

    public R getRowAt(int rowIndex)
    {
        return rows.get(rowIndex);
    }

    public List<R> getRows()
    {
        return rows;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        return columnDefintions.get(columnIndex).columnValue(getRowAt(rowIndex));
    }

    @Override
    public String getColumnName(int column)
    {
        return columnDefintions.get(column).getName();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return columnDefintions.get(columnIndex).getType();
    }

    public static class Column<R, C>
    {
        private final String name;
        private final Class<C> type;
        private final Function<R, C> valueFunction;

        public static <C, T> Column<C, T> column(String name, Class<T> type, Function<C, T> valueFunction)
        {
            return new Column<>(name, type, valueFunction);
        }

        public static <C> Column<C, String> column(String name, Function<C, String> valueFunction)
        {
            return column(name, String.class, valueFunction);
        }

        public Column(String name, Class<C> type, Function<R, C> valueFunction)
        {
            this.name = name;
            this.type = type;
            this.valueFunction = valueFunction;
        }

        public String getName()
        {
            return name;
        }

        public Class<C> getType()
        {
            return type;
        }

        public C columnValue(R row)
        {
            return valueFunction.apply(row);
        }
    }
}
