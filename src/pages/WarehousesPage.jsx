import React, { useState, useEffect } from 'react';
import { warehouseApi } from '../api/warehouseApi.js';

export default function WarehousesPage() {
  const [warehouses, setWarehouses] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    address: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'USA',
    isActive: true,
  });

  useEffect(() => {
    loadWarehouses();
  }, []);

  const loadWarehouses = async () => {
    try {
      const data = await warehouseApi.getAll();
      setWarehouses(data || []);
    } catch (err) {
      console.error('Failed to load warehouses:', err);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await warehouseApi.create(formData);
      setShowModal(false);
      setFormData({
        code: '',
        name: '',
        address: '',
        city: '',
        state: '',
        postalCode: '',
        country: 'USA',
        isActive: true,
      });
      loadWarehouses();
    } catch (err) {
      alert(err.message || 'Error creating warehouse. Ensure all fields are filled.');
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, color: '#0f172a' }}>Warehouse Management</h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
            Geographical storage hubs registered in MySQL.
          </p>
        </div>
        <button onClick={() => setShowModal(true)} style={styles.primaryBtn}>
          + New Warehouse
        </button>
      </div>

      <div style={styles.grid}>
        {warehouses.length === 0 ? (
          <p style={{ color: '#94a3b8' }}>No warehouses found. Click "+ New Warehouse" to add one.</p>
        ) : (
          warehouses.map((wh) => (
            <div key={wh.id} style={styles.card}>
              <div style={{ marginBottom: 8 }}>
                <h4 style={{ margin: 0, color: '#0f172a' }}>{wh.name}</h4>
                <span style={styles.codeBadge}>{wh.code}</span>
              </div>
              <p style={{ fontSize: 13, color: '#475569', margin: '4px 0' }}>{wh.address}</p>
              <p style={{ fontSize: 13, color: '#475569', margin: '4px 0' }}>
                {wh.city}, {wh.state} {wh.postalCode} - {wh.country}
              </p>
              <div style={{ marginTop: 12, borderTop: '1px solid #f1f5f9', paddingTop: 8 }}>
                <span style={{ fontSize: 12, color: wh.status ? '#059669' : '#dc2626', fontWeight: 600 }}>
                  ● {wh.status ? 'Active Hub' : 'Inactive'}
                </span>
              </div>
            </div>
          ))
        )}
      </div>

      {showModal && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0 }}>Add New Warehouse</h3>
            <form onSubmit={handleSubmit}>
              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Warehouse Code *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. WH-NORTH-01"
                    style={styles.input}
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  />
                </div>
                <div style={{ flex: 2 }}>
                  <label style={styles.label}>Facility Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Northern Hub"
                    style={styles.input}
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Street Address *</label>
                <input
                  type="text"
                  required
                  style={styles.input}
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                />
              </div>

              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>City *</label>
                  <input
                    type="text"
                    required
                    style={styles.input}
                    value={formData.city}
                    onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>State *</label>
                  <input
                    type="text"
                    required
                    style={styles.input}
                    value={formData.state}
                    onChange={(e) => setFormData({ ...formData, state: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Postal Code</label>
                  <input
                    type="text"
                    style={styles.input}
                    value={formData.postalCode}
                    onChange={(e) => setFormData({ ...formData, postalCode: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={styles.label}>Country *</label>
                <input
                  type="text"
                  required
                  style={styles.input}
                  value={formData.country}
                  onChange={(e) => setFormData({ ...formData, country: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button type="button" onClick={() => setShowModal(false)} style={styles.cancelBtn}>
                  Cancel
                </button>
                <button type="submit" style={styles.primaryBtn}>
                  Create Warehouse
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
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 },
  card: { background: '#fff', padding: 18, borderRadius: 8, border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' },
  codeBadge: { fontSize: 11, background: '#f1f5f9', color: '#475569', padding: '2px 6px', borderRadius: 4, fontWeight: 700 },
  modalOverlay: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  modalContent: { background: '#fff', width: 480, padding: 24, borderRadius: 8 },
  formRow: { display: 'flex', gap: 12, marginBottom: 12 },
  label: { display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 },
  input: { width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1', boxSizing: 'border-box' },
};