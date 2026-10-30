import React, { useState, useEffect } from 'react';
import { inventoryApi } from '../api/inventoryApi.js';
import { warehouseApi } from '../api/warehouseApi.js';
import { productApi } from '../api/productApi.js';

export default function InventoryPage() {
  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedWarehouse, setSelectedWarehouse] = useState('');
  const [balances, setBalances] = useState([]);
  const [movements, setMovements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [adjustData, setAdjustData] = useState({ productId: '', quantityChange: 0, reason: '' });
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    loadInitialData();
    loadMovements();
  }, []);

  useEffect(() => {
    if (selectedWarehouse) {
      loadBalances(selectedWarehouse);
    }
  }, [selectedWarehouse]);

  const loadInitialData = async () => {
    try {
      const [whData, prodData] = await Promise.all([
        warehouseApi.getAll(),
        productApi.getAll(0, 100),
      ]);
      setWarehouses(whData || []);
      if (whData && whData.length > 0) {
        setSelectedWarehouse(whData[0].id);
      }
      setProducts(prodData?.content || prodData || []);
    } catch (err) {
      setError('Failed to connect to backend or load initial lookup data.');
    }
  };

  const loadBalances = async (whId) => {
    setLoading(true);
    try {
      const data = await inventoryApi.getByWarehouse(whId);
      setBalances(data || []);
    } catch (err) {
      setError(err.message || 'Failed to fetch inventory balances.');
    } finally {
      setLoading(false);
    }
  };

  const loadMovements = async () => {
    try {
      const data = await inventoryApi.getMovements(0, 15);
      setMovements(data?.content || data || []);
    } catch (err) {
      console.error('Failed to load ledger', err);
    }
  };

  const handleAdjustSubmit = async (e) => {
    e.preventDefault();
    try {
      await inventoryApi.adjust({
        warehouseId: Number(selectedWarehouse),
        productId: Number(adjustData.productId),
        quantityChange: Number(adjustData.quantityChange),
        reason: adjustData.reason,
      });
      setShowModal(false);
      setAdjustData({ productId: '', quantityChange: 0, reason: '' });
      loadBalances(selectedWarehouse);
      loadMovements();
    } catch (err) {
      alert(err.message || 'Adjustment rejected by server invariant check.');
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={styles.topBar}>
        <div>
          <h2 style={{ margin: 0, color: '#0f172a' }}>Warehouse Inventory Balances</h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
            Invariant: <b>Available = On-Hand - Reserved</b>
          </p>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <select
            value={selectedWarehouse}
            onChange={(e) => setSelectedWarehouse(e.target.value)}
            style={styles.select}
          >
            {warehouses.map((wh) => (
              <option key={wh.id} value={wh.id}>
                {wh.code} - {wh.name}
              </option>
            ))}
          </select>

          <button onClick={() => setShowModal(true)} style={styles.primaryBtn}>
            Adjust Stock
          </button>
          <button onClick={() => loadBalances(selectedWarehouse)} style={styles.iconBtn}>
            Refresh
          </button>
        </div>
      </div>

      {error && <div style={styles.errorBanner}>{error}</div>}

      <div style={styles.card}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Product SKU</th>
              <th style={styles.th}>On-Hand (Physical)</th>
              <th style={styles.th}>Reserved (Locked)</th>
              <th style={styles.th}>Available (Free to Sell)</th>
            </tr>
          </thead>
          <tbody>
            {balances.length === 0 ? (
              <tr>
                <td colSpan="4" style={{ textAlign: 'center', padding: 24, color: '#94a3b8' }}>
                  {loading ? 'Reading database...' : 'No inventory rows found for this warehouse.'}
                </td>
              </tr>
            ) : (
              balances.map((b) => (
                <tr key={b.id} style={styles.tr}>
                  <td style={styles.td}><b>{b.productSku || `PROD-${b.productId}`}</b></td>
                  <td style={styles.td}>{b.onHandQty}</td>
                  <td style={{ ...styles.td, color: '#d97706', fontWeight: 600 }}>{b.reservedQty}</td>
                  <td style={{ ...styles.td, color: '#059669', fontWeight: 700 }}>
                    {b.availableQty !== undefined ? b.availableQty : b.onHandQty - b.reservedQty}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <h3 style={{ marginTop: 32, marginBottom: 12, color: '#0f172a' }}>
        Recent Audit Movements (Immutable Ledger)
      </h3>
      <div style={styles.card}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Time</th>
              <th style={styles.th}>Movement Type</th>
              <th style={styles.th}>Delta</th>
              <th style={styles.th}>Reference</th>
              <th style={styles.th}>Reason</th>
              <th style={styles.th}>Authorized By</th>
            </tr>
          </thead>
          <tbody>
            {movements.length === 0 ? (
              <tr>
                <td colSpan="6" style={{ textAlign: 'center', padding: 24, color: '#94a3b8' }}>
                  No movement audit logs found.
                </td>
              </tr>
            ) : (
              movements.map((m) => (
                <tr key={m.id} style={styles.tr}>
                  <td style={{ ...styles.td, fontSize: 12, color: '#64748b' }}>
                    {new Date(m.createdAt || m.timestamp).toLocaleString()}
                  </td>
                  <td style={styles.td}>
                    <span style={styles.typeBadge}>{m.movementType}</span>
                  </td>
                  <td style={{ ...styles.td, fontWeight: 700, color: m.quantityChange > 0 ? '#059669' : '#dc2626' }}>
                    {m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange}
                  </td>
                  <td style={styles.td}>{m.referenceType} #{m.referenceId}</td>
                  <td style={styles.td}>{m.reason || '-'}</td>
                  <td style={styles.td}><b>{m.performedBy || 'admin'}</b></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0 }}>Manual Stock Adjustment</h3>
            <form onSubmit={handleAdjustSubmit}>
              <div style={{ marginBottom: 14 }}>
                <label style={styles.label}>Product</label>
                <select
                  required
                  style={styles.input}
                  value={adjustData.productId}
                  onChange={(e) => setAdjustData({ ...adjustData, productId: e.target.value })}
                >
                  <option value="">Select SKU...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} - {p.name}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={styles.label}>Quantity Change (+/-)</label>
                <input
                  type="number"
                  required
                  style={styles.input}
                  value={adjustData.quantityChange}
                  onChange={(e) => setAdjustData({ ...adjustData, quantityChange: e.target.value })}
                />
              </div>

              <div style={{ marginBottom: 18 }}>
                <label style={styles.label}>Reason for Audit</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Physical inventory recount reconciliation"
                  style={styles.input}
                  value={adjustData.reason}
                  onChange={(e) => setAdjustData({ ...adjustData, reason: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button type="button" onClick={() => setShowModal(false)} style={styles.cancelBtn}>
                  Cancel
                </button>
                <button type="submit" style={styles.primaryBtn}>
                  Submit Adjustment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

const styles = {
  topBar: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  select: { padding: '8px 12px', borderRadius: 6, border: '1px solid #cbd5e1', fontSize: 14 },
  primaryBtn: {
    backgroundColor: '#0284c7',
    color: '#fff',
    border: 'none',
    padding: '8px 16px',
    borderRadius: 6,
    fontWeight: 600,
    cursor: 'pointer',
  },
  cancelBtn: { padding: '8px 16px', background: '#e2e8f0', border: 'none', borderRadius: 6, cursor: 'pointer' },
  iconBtn: { padding: '8px 14px', border: '1px solid #cbd5e1', borderRadius: 6, background: '#fff', cursor: 'pointer' },
  card: { backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)', overflow: 'hidden' },
  table: { width: '100%', borderCollapse: 'collapse', textAlign: 'left' },
  th: { padding: '12px 16px', backgroundColor: '#f8fafc', color: '#475569', fontSize: 13, borderBottom: '1px solid #e2e8f0' },
  tr: { borderBottom: '1px solid #f1f5f9' },
  td: { padding: '12px 16px', fontSize: 14, color: '#1e293b' },
  typeBadge: { background: '#e0f2fe', color: '#0369a1', padding: '3px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600 },
  errorBanner: { background: '#fee2e2', color: '#b91c1c', padding: 12, borderRadius: 6, marginBottom: 16 },
  modalOverlay: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  modalContent: { background: '#fff', width: 440, padding: 24, borderRadius: 8 },
  label: { display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 },
  input: { width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1', boxSizing: 'border-box' },
};