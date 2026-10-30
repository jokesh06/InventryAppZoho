import React, { useState, useEffect } from 'react';
import { inventoryApi } from '../api/inventoryApi.js';
import { warehouseApi } from '../api/warehouseApi.js';
import { productApi } from '../api/productApi.js';

export default function WarehouseStockCheckPage() {
  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  
  // Search state
  const [selectedWarehouseId, setSelectedWarehouseId] = useState('');
  const [searchProductId, setSearchProductId] = useState('');
  const [resultBalance, setResultBalance] = useState(null);
  const [searchedProductInfo, setSearchedProductInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    loadLookups();
  }, []);

  const loadLookups = async () => {
    try {
      const [whData, prodData] = await Promise.all([
        warehouseApi.getAll(),
        productApi.getAll(0, 100),
      ]);
      const wList = whData || [];
      const pList = prodData?.content || prodData || [];
      setWarehouses(wList);
      setProducts(pList);
      if (wList.length > 0) setSelectedWarehouseId(wList[0].id);
      if (pList.length > 0) setSearchProductId(pList[0].id);
    } catch (err) {
      console.error(err);
    }
  };

  const handleSearchCheck = async (e) => {
    e.preventDefault();
    if (!selectedWarehouseId || !searchProductId) return;

    setLoading(true);
    setNotFound(false);
    setResultBalance(null);
    setSearchedProductInfo(null);

    try {
      // 1. Fetch exact product metadata by ID
      const productData = await productApi.getById(searchProductId);
      setSearchedProductInfo(productData);

      // 2. Fetch specific stock balance in the warehouse
      const balanceData = await inventoryApi.getBalance(selectedWarehouseId, searchProductId);
      if (!balanceData || (balanceData.onHandQty === 0 && balanceData.reservedQty === 0)) {
        setNotFound(true);
      }
      setResultBalance(balanceData);
    } catch (err) {
      setNotFound(true);
    } finally {
      setLoading(false);
    }
  };

  const selectedWarehouseObj = warehouses.find((w) => w.id === Number(selectedWarehouseId));

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ margin: 0, color: '#0f172a' }}>Warehouse Stock & SKU Inspector</h2>
        <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
          Query any warehouse node directly to verify whether an SKU is currently stocked, on hold, or zeroed out.
        </p>
      </div>

      {/* Query Bar */}
      <form onSubmit={handleSearchCheck} style={styles.searchCard}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 200 }}>
            <label style={styles.label}>Select Warehouse Facility</label>
            <select
              style={styles.input}
              value={selectedWarehouseId}
              onChange={(e) => setSelectedWarehouseId(e.target.value)}
            >
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.code} - {w.name}
                </option>
              ))}
            </select>
          </div>

          <div style={{ flex: 1, minWidth: 200 }}>
            <label style={styles.label}>Product ID or SKU</label>
            <select
              style={styles.input}
              value={searchProductId}
              onChange={(e) => setSearchProductId(e.target.value)}
            >
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  [ID #{p.id}] {p.sku} - {p.name}
                </option>
              ))}
            </select>
          </div>

          <button type="submit" disabled={loading} style={styles.primaryBtn}>
            {loading ? 'Querying...' : 'Verify Warehouse Stock'}
          </button>
        </div>
      </form>

      {/* Search Result Box */}
      {searchedProductInfo && (
        <div style={styles.resultsGrid}>
          {/* Product Overview Card */}
          <div style={styles.card}>
            <span style={styles.skuTag}>{searchedProductInfo.sku}</span>
            <h3 style={{ margin: '8px 0 4px', color: '#0f172a' }}>{searchedProductInfo.name}</h3>
            <p style={{ color: '#64748b', fontSize: 13, margin: '0 0 12px' }}>
              {searchedProductInfo.description || 'Master catalog SKU item'}
            </p>
            <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: 10, fontSize: 13 }}>
              <div>Catalog Price: <b>${Number(searchedProductInfo.unitSellingPrice || 0).toFixed(2)}</b></div>
              <div>Safety Reorder Point: <b>{searchedProductInfo.reorderLevel || 10} units</b></div>
            </div>
          </div>

          {/* Warehouse Real-time Quantities */}
          <div style={styles.card}>
            <h4 style={{ margin: '0 0 12px 0', color: '#0f172a' }}>
              Stock at: {selectedWarehouseObj?.name} ({selectedWarehouseObj?.code})
            </h4>

            {notFound ? (
              <div style={styles.emptyWarning}>
                <b>Out of Stock / Unregistered:</b> This product has no active balance row in this warehouse node.
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
                <div style={styles.metricBox}>
                  <div style={styles.metricLabel}>On-Hand (Physical)</div>
                  <div style={styles.metricVal}>{resultBalance?.onHandQty ?? 0}</div>
                </div>
                <div style={styles.metricBox}>
                  <div style={styles.metricLabel}>Reserved (Held)</div>
                  <div style={{ ...styles.metricVal, color: '#d97706' }}>
                    {resultBalance?.reservedQty ?? 0}
                  </div>
                </div>
                <div style={styles.metricBox}>
                  <div style={styles.metricLabel}>Available to Sell</div>
                  <div style={{ ...styles.metricVal, color: '#059669' }}>
                    {resultBalance?.availableQty !== undefined
                      ? resultBalance.availableQty
                      : (resultBalance?.onHandQty ?? 0) - (resultBalance?.reservedQty ?? 0)}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

const styles = {
  searchCard: {
    background: '#fff',
    padding: 20,
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    marginBottom: 20,
  },
  label: { display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6, color: '#334155' },
  input: {
    width: '100%',
    padding: '9px 12px',
    borderRadius: 6,
    border: '1px solid #cbd5e1',
    boxSizing: 'border-box',
    fontSize: 14,
  },
  primaryBtn: {
    backgroundColor: '#0284c7',
    color: '#fff',
    border: 'none',
    padding: '10px 18px',
    borderRadius: 6,
    fontWeight: 600,
    cursor: 'pointer',
    height: 38,
  },
  resultsGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1.5fr',
    gap: 16,
  },
  card: {
    background: '#fff',
    padding: 20,
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  skuTag: {
    background: '#e0f2fe',
    color: '#0369a1',
    fontWeight: 700,
    fontSize: 12,
    padding: '3px 8px',
    borderRadius: 4,
  },
  emptyWarning: {
    background: '#fef2f2',
    color: '#991b1b',
    padding: 14,
    borderRadius: 6,
    fontSize: 13,
    border: '1px solid #fee2e2',
  },
  metricBox: {
    background: '#f8fafc',
    padding: 12,
    borderRadius: 6,
    textAlign: 'center',
    border: '1px solid #e2e8f0',
  },
  metricLabel: { fontSize: 11, color: '#64748b', fontWeight: 600, marginBottom: 4 },
  metricVal: { fontSize: 22, fontWeight: 800, color: '#0f172a' },
};