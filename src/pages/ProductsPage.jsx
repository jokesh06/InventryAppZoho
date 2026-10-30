import React, { useState, useEffect } from 'react';
import { productApi } from '../api/productApi.js';

export default function ProductsPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);

  const [formData, setFormData] = useState({
    sku: '',
    name: '',
    description: '',
    categoryId: 1,
    unitSellingPrice: '',
    reorderLevel: 10,
  });

  useEffect(() => {
    loadProducts();
  }, []);

  const loadProducts = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await productApi.getAll(0, 100);
      // Handles both Spring Data Page objects (data.content) and raw arrays (data)
      setProducts(data?.content || data || []);
    } catch (err) {
      setError(err.message || 'Failed to fetch products from backend database.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await productApi.create({
        sku: formData.sku.trim().toUpperCase(),
        name: formData.name.trim(),
        description: formData.description,
        categoryId: Number(formData.categoryId),
        unitSellingPrice: parseFloat(formData.unitSellingPrice),
        reorderLevel: Number(formData.reorderLevel),
      });

      setShowModal(false);
      setFormData({
        sku: '',
        name: '',
        description: '',
        categoryId: 1,
        unitSellingPrice: '',
        reorderLevel: 10,
      });
      loadProducts();
    } catch (err) {
      alert(err.message || 'Failed to register product SKU.');
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={styles.topBar}>
        <div>
          <h2 style={{ margin: 0, color: '#0f172a' }}>Product Catalog</h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
            Master SKU directory, baseline retail pricing, and minimum stock reorder alerts.
          </p>
        </div>
        <button onClick={() => setShowModal(true)} style={styles.primaryBtn}>
          + New Product
        </button>
      </div>

      {error && <div style={styles.errorBanner}>{error}</div>}

      <div style={styles.card}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>SKU Barcode</th>
              <th style={styles.th}>Product Title</th>
              <th style={styles.th}>Description</th>
              <th style={styles.th}>Unit Price</th>
              <th style={styles.th}>Reorder Threshold</th>
              <th style={styles.th}>Status</th>
            </tr>
          </thead>
          <tbody>
            {products.length === 0 ? (
              <tr>
                <td colSpan="6" style={{ textAlign: 'center', padding: 28, color: '#94a3b8' }}>
                  {loading ? 'Reading MySQL catalog...' : 'No products found. Click "+ New Product" to register one.'}
                </td>
              </tr>
            ) : (
              products.map((p) => (
                <tr key={p.id} style={styles.tr}>
                  <td style={styles.td}>
                    <span style={styles.skuBadge}>{p.sku}</span>
                  </td>
                  <td style={{ ...styles.td, fontWeight: 600 }}>{p.name}</td>
                  <td style={{ ...styles.td, color: '#64748b', fontSize: 13 }}>
                    {p.description || '-'}
                  </td>
                  <td style={{ ...styles.td, color: '#0f172a', fontWeight: 700 }}>
                    ${Number(p.unitSellingPrice || 0).toFixed(2)}
                  </td>
                  <td style={styles.td}>
                    <span style={styles.reorderBadge}>
                      {p.reorderLevel || 10} units
                    </span>
                  </td>
                  <td style={styles.td}>
                    <span style={{ fontSize: 12, color: p.isActive !== false ? '#059669' : '#dc2626', fontWeight: 600 }}>
                      ● {p.isActive !== false ? 'Active' : 'Archived'}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0, color: '#0f172a' }}>Register Catalog Product</h3>
            <form onSubmit={handleSubmit}>
              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>SKU Barcode *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. SKU-MON-4K"
                    style={styles.input}
                    value={formData.sku}
                    onChange={(e) => setFormData({ ...formData, sku: e.target.value })}
                  />
                </div>
                <div style={{ flex: 2 }}>
                  <label style={styles.label}>Product Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Dell UltraSharp 32-inch 4K Monitor"
                    style={styles.input}
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Description</label>
                <input
                  type="text"
                  placeholder="Technical specifications or dimensions"
                  style={styles.input}
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                />
              </div>

              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Category ID *</label>
                  <input
                    type="number"
                    min="1"
                    required
                    style={styles.input}
                    value={formData.categoryId}
                    onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Unit Retail Price ($) *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    placeholder="799.99"
                    style={styles.input}
                    value={formData.unitSellingPrice}
                    onChange={(e) => setFormData({ ...formData, unitSellingPrice: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Reorder Level *</label>
                  <input
                    type="number"
                    min="1"
                    required
                    style={styles.input}
                    value={formData.reorderLevel}
                    onChange={(e) => setFormData({ ...formData, reorderLevel: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
                <button type="button" onClick={() => setShowModal(false)} style={styles.cancelBtn}>
                  Cancel
                </button>
                <button type="submit" style={styles.primaryBtn}>
                  Create Product
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
  card: { backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)', overflow: 'hidden' },
  table: { width: '100%', borderCollapse: 'collapse', textAlign: 'left' },
  th: { padding: '12px 16px', backgroundColor: '#f8fafc', color: '#475569', fontSize: 13, borderBottom: '1px solid #e2e8f0' },
  tr: { borderBottom: '1px solid #f1f5f9' },
  td: { padding: '12px 16px', fontSize: 14 },
  skuBadge: { background: '#f1f5f9', color: '#0369a1', padding: '3px 8px', borderRadius: 4, fontSize: 12, fontWeight: 700 },
  reorderBadge: { background: '#fef3c7', color: '#b45309', padding: '2px 6px', borderRadius: 4, fontSize: 12, fontWeight: 600 },
  errorBanner: { background: '#fee2e2', color: '#b91c1c', padding: 12, borderRadius: 6, marginBottom: 16 },
  modalOverlay: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  modalContent: { background: '#fff', width: 520, padding: 24, borderRadius: 8 },
  formRow: { display: 'flex', gap: 12, marginBottom: 12 },
  label: { display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4, color: '#334155' },
  input: { width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1', boxSizing: 'border-box' },
};