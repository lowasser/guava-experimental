package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

final class DenseImmutableTable<R, C, V> extends ImmutableTable<R, C, V> {
  private transient final ImmutableSet<R> rowKeySet;
  private transient final ImmutableSet<C> columnKeySet;
  private transient final int[] rowCounts;
  private transient final int[] columnCounts;
  private transient final int[] cellIndices;
  private transient final V[] values;
  
  static <R, C, V> DenseImmutableTable<R, C, V> create(
      Collection<? extends Cell<? extends R, ? extends C, ? extends V>> cells,
      ImmutableSet<R> rowKeys, ImmutableSet<C> columnKeys) {
    int[] rowCounts = new int[rowKeys.size()];
    int[] columnCounts = new int[columnKeys.size()];
    int[] cellIndices = new int[cells.size()];
    @SuppressWarnings("unchecked")
    V[] values = (V[]) new Object[rowKeys.size() * columnKeys.size()];
    int i = 0;
    for (Cell<? extends R, ? extends C, ? extends V> cell : cells) {
      R r = cell.getRowKey();
      C c = cell.getColumnKey();
      V v = cell.getValue();
      
      int rowIndex = rowKeys.indexOf(r);
      int columnIndex = columnKeys.indexOf(c);
      int cellIndex = rowIndex * columnKeys.size() + columnIndex;
      
      rowCounts[rowIndex]++;
      columnCounts[columnIndex]++;
      values[cellIndex] = checkNotNull(v);
      cellIndices[i++] = cellIndex;
    }
    return new DenseImmutableTable<R, C, V>(rowKeys, columnKeys, rowCounts, columnCounts, cellIndices, values);
  }

  private DenseImmutableTable(
      ImmutableSet<R> rowKeys,
      ImmutableSet<C> columnKeys,
      int[] rowCounts,
      int[] columnCounts,
      int[] cellIndices,
      V[] values) {
    this.rowKeySet = rowKeys;
    this.columnKeySet = columnKeys;
    this.rowCounts = rowCounts;
    this.columnCounts = columnCounts;
    this.cellIndices = cellIndices;
    this.values = values;
  }

  @Override
  public boolean contains(@Nullable Object rowKey, @Nullable Object columnKey) {
    int rowIndex = rowKeySet.indexOf(rowKey);
    if (rowIndex != -1) {
      int columnIndex = columnKeySet.indexOf(columnKey);
      if (columnIndex != -1) {
        int cellIndex = rowIndex * columnKeySet.size() + columnIndex;
        return values[cellIndex] != null;
      }
    }
    return false;
  }

  @Override
  public boolean containsRow(@Nullable Object rowKey) {
    return rowKeySet.contains(rowKey);
  }

  @Override
  public boolean containsColumn(@Nullable Object columnKey) {
    return columnKeySet.contains(columnKey);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    if (value == null) {
      return false;
    }
    for (int cellIndex : cellIndices) {
      if (value.equals(values[cellIndex])) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(@Nullable Object rowKey, @Nullable Object columnKey) {
    int rowIndex = rowKeySet.indexOf(rowKey);
    if (rowIndex != -1) {
      int columnIndex = columnKeySet.indexOf(columnKey);
      if (columnIndex != -1) {
        return values[rowIndex * columnKeySet.size() + columnIndex];
      }
    }
    return null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return cellIndices.length;
  }

  private transient ImmutableCollection<V> valueCollection;
  
  @Override
  public ImmutableCollection<V> values() {
    ImmutableCollection<V> result = valueCollection;
    return (result == null) ? valueCollection = new Values() : result;
  }
  
  private final class Values extends ImmutableList<V> {
    @Override
    public int size() {
      return cellIndices.length;
    }

    @Override
    public V get(int index) {
      return values[cellIndices[index]];
    }

    @Override
    boolean isPartialView() {
      return true;
    }
  }

  @Override
  public ImmutableSet<Cell<R, C, V>> cellSet() {
    // TODO Auto-generated method stub
    return null;
  }
  
  private final V getValue(int rowIndex, int columnIndex) {
    return values[rowIndex * columnKeySet.size() + columnIndex];
  }
  
  private final class CellSet extends ImmutableSet<Cell<R, C, V>> {
    @Override
    public int size() {
      return cellIndices.length;
    }

    @Override
    public boolean contains(@Nullable Object object) {
      if (object instanceof Cell) {
        Cell<?, ?, ?> cell = (Cell<?, ?, ?>) object;
        V value = get(cell.getRowKey(), cell.getColumnKey());
        return value != null && value.equals(cell.getValue());
      }
      return false;
    }

    @Override
    public UnmodifiableIterator<Cell<R, C, V>> iterator() {
      return new AbstractIndexedListIterator<Cell<R, C, V>>(size()) {
        @Override
        protected Cell<R, C, V> get(int index) {
          int cellIndex = cellIndices[index];
          int columnIndex = cellIndex % columnKeySet.size();
          int rowIndex = cellIndex / columnKeySet.size();
          R rowKey = rowKeySet.asList().get(rowIndex);
          C columnKey = columnKeySet.asList().get(columnIndex);
          V value = values[cellIndex];
          return Tables.immutableCell(rowKey, columnKey, value);
        }
      };
    }

    @Override
    boolean isPartialView() {
      return true;
    }
  }
  
  private static abstract class DenseIndexedMap<K, V> extends ImmutableMap<K, V> {
    private final ImmutableSet<K> keySpace;
    private final int size;
    
    DenseIndexedMap(ImmutableSet<K> keySpace, int size) {
      this.keySpace = keySpace;
      this.size = size;
    }

    @Nullable abstract V getValue(int keyIndex);

    @Override
    public int size() {
      return size;
    }

    @Override
    public V get(@Nullable Object key) {
      int index = keySpace.indexOf(key);
      return (index == -1) ? null : getValue(index);
    }

    @Override
    ImmutableSet<Entry<K, V>> createEntrySet() {
      return new ImmutableMapEntrySet<K, V>() {
        @Override
        ImmutableMap<K, V> map() {
          return DenseIndexedMap.this;
        }

        @Override
        public UnmodifiableIterator<Entry<K, V>> iterator() {
          return new AbstractIterator<Entry<K, V>>() {
            int index = -1;
            final ImmutableList<K> keyList = keySpace.asList();
            
            @Override
            protected Entry<K, V> computeNext() {
              while (++index < keyList.size()) {
                V value = getValue(index);
                if (value != null) {
                  return Maps.immutableEntry(keyList.get(index), value);
                }
              }
              return endOfData();
            }
          };
        }
      };
    }

    @Override
    boolean isPartialView() {
      return true;
    } 
  }

  @Override
  public ImmutableMap<R, V> column(C columnKey) {
    final int columnIndex = columnKeySet.indexOf(columnKey);
    if (columnIndex == -1) {
      return ImmutableMap.of();
    } else {
      return columnByIndex(columnIndex);
    }
  }

  private ImmutableMap<R, V> columnByIndex(final int columnIndex) {
    return new DenseIndexedMap<R, V>(rowKeySet, columnCounts[columnIndex]) {
      @Override
      @Nullable
      V getValue(int rowIndex) {
        return DenseImmutableTable.this.getValue(rowIndex, columnIndex);
      }
    };
  }

  @Override
  public ImmutableSet<C> columnKeySet() {
    return columnKeySet;
  }
  
  private transient ImmutableMap<C, Map<R, V>> columnMap;

  @Override
  public ImmutableMap<C, Map<R, V>> columnMap() {
    ImmutableMap<C, Map<R, V>> result = columnMap;
    if (result == null) {
      result = columnMap = new DenseIndexedMap<C, Map<R, V>>(columnKeySet, columnKeySet.size()) {
        @Override
        @Nullable
        Map<R, V> getValue(int columnIndexIndex) {
          return columnByIndex(columnIndexIndex);
        }
      };
    }
    return result;
  }

  @Override
  public ImmutableMap<C, V> row(R rowKey) {
    int rowIndex = rowKeySet.indexOf(rowKey);
    return (rowIndex == -1) ? ImmutableMap.<C, V>of() : rowByIndex(rowIndex);
  }
  
  private final ImmutableMap<C, V> rowByIndex(final int rowIndex) {
    return new DenseIndexedMap<C, V>(columnKeySet, rowCounts[rowIndex]) {
      @Override
      @Nullable
      V getValue(int columnIndex) {
        return DenseImmutableTable.this.getValue(rowIndex, columnIndex);
      }
    };
  }

  @Override
  public ImmutableSet<R> rowKeySet() {
    return rowKeySet;
  }

  private transient ImmutableMap<R, Map<C, V>> rowMap;
  
  @Override
  public ImmutableMap<R, Map<C, V>> rowMap() {
    ImmutableMap<R, Map<C, V>> result = rowMap;
    if (result == null) {
      result = rowMap = new DenseIndexedMap<R, Map<C, V>>(rowKeySet, rowKeySet.size()) {
        @Override
        @Nullable
        Map<C, V> getValue(int rowIndex) {
          return rowByIndex(rowIndex);
        }
      };
    }
    return result;
  }

}
