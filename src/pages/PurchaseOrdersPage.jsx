import React, { useState, useEffect } from 'react';
import { purchaseOrderApi } from '../api/purchaseOrderApi.js';
import { productApi } from '../api/productApi.js';
import { warehouseApi } from '../api/warehouseApi.js';

export default function PurchaseOrdersPage() {
  const [purchaseOrders, setPurchaseOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorToast, setErrorToast] = useState('');

  // Form state strictly aligned with CreatePurchaseOrderRequest.java
  const [form, setForm] = useState({
    supplierId: 1,
    destinationWarehouseId: '',
    productId: '',
    orderedQty: 50,
    unitCost: '',
  });

  useEffect(() => {
    loadLookups();
    loadPurchaseOrders();
  }, []);

  const loadPurchaseOrders = async () => {
    setLoading(true);
    try {
      const res = await purchaseOrderApi.getAll(0, 50);
      console.log('Purchase Orders API Response:', res);

      // Extract records across raw array, Page.content, or Response envelopes
      let list = [];
      if (Array.isArray(res)) {
        list = res;
      } else if (res && Array.isArray(res.content)) {
        list = res.content;
      } else if (res && Array.isArray(res.data)) {
        list = res.data;
      } else if (res && res.data && Array.isArray(res.data.content)) {
        list = res.data.content;
      }

      setPurchaseOrders(list);
    } catch (err) {
      console.error('Failed to load purchase orders:', err);
      setErrorToast('Could not load PO list from backend: ' + (err.message || 'Check GET endpoint'));
    } finally {
      setLoading(false);
    }
  };

  const loadLookups = async () => {
    try {
      const [prodData, whData] = await Promise.all([
        productApi.getAll(0, 100),
        warehouseApi.getAll(),
      ]);

      const pList = Array.isArray(prodData)
        ? prodData
        : prodData?.content || prodData?.data || [];
      const wList = Array.isArray(whData)
        ? whData
        : whData?.content || whData?.data || [];

      setProducts(pList);
      setWarehouses(wList);

      if (wList.length > 0) {
        setForm((prev) => ({ ...prev, destinationWarehouseId: wList[0].id }));
      }
    } catch (err) {
      console.error('Failed to load lookups:', err);
    }
  };

  const handleProductSelect = (e) => {
    const selectedId = e.target.value;
    if (!selectedId) {
      setForm((prev) => ({ ...prev, productId: '', unitCost: '' }));
      return;
    }
    const matched = products.find((p) => p.id === Number(selectedId));
    setForm((prev) => ({
      ...prev,
      productId: selectedId,
      unitCost: matched ? (matched.unitSellingPrice * 0.7).toFixed(2) : '10.00',
    }));
  };

  const handleCreatePO = async (e) => {
    e.preventDefault();
    setStatusMessage('');
    setErrorToast('');

    if (!form.supplierId || Number(form.supplierId) <= 0) {
      alert('Valid Supplier ID is required.');
      return;
    }
    if (!form.destinationWarehouseId) {
      alert('Please select a destination warehouse.');
      return;
    }
    if (!form.productId) {
      alert('Please select a product SKU to restock.');
      return;
    }
    if (!form.orderedQty || Number(form.orderedQty) < 1) {
      alert('Ordered quantity must be at least 1.');
      return;
    }
    if (form.unitCost === '' || Number(form.unitCost) < 0.01) {
      alert('Unit cost must be at least $0.01.');
      return;
    }

    const payload = {
      supplierId: Number(form.supplierId),
      destinationWarehouseId: Number(form.destinationWarehouseId),
      items: [
        {
          productId: Number(form.productId),
          orderedQty: Number(form.orderedQty),
          unitCost: parseFloat(Number(form.unitCost).toFixed(2)),
        },
      ],
    };

    setLoading(true);
    try {
      const createdRecord = await purchaseOrderApi.create(payload);
      console.log('PO Created Successfully:', createdRecord);

      setShowCreate(false);
      setStatusMessage('Purchase order drafted successfully.');

      // Optimistically insert so it displays immediately
      if (createdRecord && (createdRecord.id || createdRecord.poNumber)) {
        setPurchaseOrders((prev) => [createdRecord, ...prev]);
      }

      await loadPurchaseOrders();
    } catch (err) {
      console.error('PO Creation Error:', err);
      setErrorToast(err.message || 'Failed to create purchase order.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitToSupplier = async (poId) => {
    setStatusMessage('');
    setErrorToast('');
    try {
      await purchaseOrderApi.submit(poId);
      setStatusMessage(`PO #${poId} submitted to supplier. Ready for dock receiving.`);
      await loadPurchaseOrders();
    } catch (err) {
      alert(err.message || 'Failed to submit PO to supplier.');
    }
  };

  const handleReceiveDock = async (po) => {
    setStatusMessage('');
    setErrorToast('');

    try {
      // 1. Fetch full PO details to guarantee items and item IDs are loaded
      let detailedPo = null;
      try {
        detailedPo = await purchaseOrderApi.getById(po.id);
      } catch (fetchErr) {
        console.warn('Could not fetch single PO details, falling back to table state:', fetchErr);
      }

      const activePo = detailedPo || po;
      const itemsList = activePo.items || po.items || [];

      if (!itemsList || itemsList.length === 0) {
        alert(`Cannot receive: No item line entries found for PO #${po.id}. Check database items mapping.`);
        return;
      }

      const targetItem = itemsList[0];
      // Resolve purchaseOrderItemId (checking id, purchaseOrderItemId, or itemId)
      const poItemId = targetItem.id || targetItem.purchaseOrderItemId || targetItem.itemId;

      if (!poItemId) {
        alert('Missing purchaseOrderItemId on item. Inspect console for line item properties.');
        console.error('Line item object:', targetItem);
        return;
      }

      const defaultQty = targetItem.orderedQty || targetItem.quantity || 50;
      const qtyInput = prompt(
        `Dock Receiving for PO #${po.poNumber || po.id}\nItem ID: ${poItemId}\n\nEnter received pallet quantity:`,
        defaultQty
      );

      if (!qtyInput || isNaN(Number(qtyInput)) || Number(qtyInput) < 1) {
        alert('Received quantity must be a whole number of 1 or greater.');
        return;
      }

      // Structure strictly conforming to ReceiveGoodsRequest.java
      const payload = {
        receipts: [
          {
            purchaseOrderItemId: Number(poItemId),
            quantityReceived: parseInt(qtyInput, 10),
          },
        ],
      };

      console.log('Sending ReceiveGoodsRequest payload:', payload);

      await purchaseOrderApi.receive(po.id, payload);
      setStatusMessage(`Goods successfully received for PO #${po.id}! Warehouse inventory balances updated.`);
      await loadPurchaseOrders();
    } catch (err) {
      console.error('Dock Receiving Error:', err);
      alert(err.message || 'Dock receiving rejected by server.');
    }
  };

  const normalizeStatus = (status) => (status ? String(status).toUpperCase().trim() : 'DRAFT');

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, color: '#0f172a' }}>Purchase Orders (Inbound Restock)</h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
            Lifecycle: <b>DRAFT</b> → <b>SUBMITTED</b> → <b>RECEIVED</b> (Restocks physical On-Hand inventory)
          </p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button onClick={loadPurchaseOrders} style={styles.secondaryBtn}>
            Refresh
          </button>
          <button onClick={() => setShowCreate(true)} style={styles.primaryBtn}>
            + Create Restock PO
          </button>
        </div>
      </div>

      {statusMessage && (
        <div style={styles.infoBanner}>
          <b>Notice:</b> {statusMessage}
        </div>
      )}

      {errorToast && (
        <div style={styles.errorBanner}>
          <b>Alert:</b> {errorToast}
        </div>
      )}

      <div style={styles.card}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>PO #</th>
              <th style={styles.th}>Supplier</th>
              <th style={styles.th}>Destination WH</th>
              <th style={styles.th}>Total Value</th>
              <th style={styles.th}>Status</th>
              <th style={styles.th}>Date</th>
              <th style={styles.th}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {purchaseOrders.length === 0 ? (
              <tr>
                <td colSpan="7" style={{ textAlign: 'center', padding: 28, color: '#94a3b8' }}>
                  {loading
                    ? 'Reading procurement database...'
                    : 'No purchase orders recorded yet. Click "+ Create Restock PO" above.'}
                </td>
              </tr>
            ) : (
              purchaseOrders.map((po) => {
                const currentStatus = normalizeStatus(po.status);
                return (
                  <tr key={po.id} style={styles.tr}>
                    <td style={styles.td}><b>{po.poNumber || `PO-${po.id}`}</b></td>
                    <td style={styles.td}>Supplier #{po.supplierId || 1}</td>
                    <td style={styles.td}>
                      WH #{po.destinationWarehouseId || po.targetWarehouseId || (po.warehouse && po.warehouse.id) || '-'}
                    </td>
                    <td style={{ ...styles.td, fontWeight: 700, color: '#0f172a' }}>
                      ${Number(po.totalAmount || 0).toFixed(2)}
                    </td>
                    <td style={styles.td}>
                      <span style={{ ...styles.badge, ...getStatusBadge(currentStatus) }}>
                        {currentStatus}
                      </span>
                    </td>
                    <td style={{ ...styles.td, fontSize: 13, color: '#64748b' }}>
                      {new Date(po.createdAt || Date.now()).toLocaleDateString()}
                    </td>
                    <td style={styles.td}>
                      {currentStatus === 'DRAFT' && (
                        <button onClick={() => handleSubmitToSupplier(po.id)} style={styles.actionBtnSubmit}>
                          Submit to Supplier
                        </button>
                      )}
                      {currentStatus === 'SUBMITTED' && (
                        <button onClick={() => handleReceiveDock(po)} style={styles.actionBtnReceive}>
                          Receive at Dock
                        </button>
                      )}
                      {currentStatus === 'RECEIVED' && (
                        <span style={{ fontSize: 12, color: '#059669', fontWeight: 600 }}>
                          ✓ Stock Received
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Creation Modal */}
      {showCreate && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0, color: '#0f172a' }}>Draft Restock Purchase Order</h3>
            <form onSubmit={handleCreatePO}>
              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Supplier ID *</label>
                <input
                  type="number"
                  min="1"
                  required
                  style={styles.input}
                  value={form.supplierId}
                  onChange={(e) => setForm({ ...form, supplierId: e.target.value })}
                />
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Destination Warehouse *</label>
                <select
                  required
                  style={styles.input}
                  value={form.destinationWarehouseId}
                  onChange={(e) => setForm({ ...form, destinationWarehouseId: e.target.value })}
                >
                  <option value="">Select destination warehouse...</option>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.code} - {w.name}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Product SKU to Procure *</label>
                <select
                  required
                  style={styles.input}
                  value={form.productId}
                  onChange={handleProductSelect}
                >
                  <option value="">Select product SKU...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} - {p.name}
                    </option>
                  ))}
                </select>
              </div>

              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Ordered Qty *</label>
                  <input
                    type="number"
                    min="1"
                    required
                    style={styles.input}
                    value={form.orderedQty}
                    onChange={(e) => setForm({ ...form, orderedQty: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Unit Cost ($) *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    style={styles.input}
                    value={form.unitCost}
                    onChange={(e) => setForm({ ...form, unitCost: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  style={styles.cancelBtn}
                  disabled={loading}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={styles.primaryBtn}
                  disabled={loading}
                >
                  {loading ? 'Saving...' : 'Save as Draft'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

const getStatusBadge = (status) => {
  switch (status) {
    case 'DRAFT':
      return { background: '#fef3c7', color: '#b45309' };
    case 'SUBMITTED':
      return { background: '#e0f2fe', color: '#0369a1' };
    case 'RECEIVED':
      return { background: '#dcfce7', color: '#15803d' };
    default:
      return { background: '#f1f5f9', color: '#475569' };
  }
};

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
  secondaryBtn: {
    backgroundColor: '#fff',
    color: '#475569',
    border: '1px solid #cbd5e1',
    padding: '8px 14px',
    borderRadius: 6,
    fontWeight: 600,
    cursor: 'pointer',
  },
  cancelBtn: {
    padding: '8px 16px',
    background: '#e2e8f0',
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
    color: '#334155',
  },
  actionBtnSubmit: {
    background: '#0284c7',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 12,
    fontWeight: 600,
  },
  actionBtnReceive: {
    background: '#16a34a',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 12,
    fontWeight: 600,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
    overflow: 'hidden',
  },
  table: { width: '100%', borderCollapse: 'collapse', textAlign: 'left' },
  th: {
    padding: '12px 16px',
    backgroundColor: '#f8fafc',
    color: '#475569',
    fontSize: 13,
    borderBottom: '1px solid #e2e8f0',
  },
  tr: { borderBottom: '1px solid #f1f5f9' },
  td: { padding: '12px 16px', fontSize: 14 },
  badge: { padding: '3px 8px', borderRadius: 4, fontSize: 12, fontWeight: 700 },
  infoBanner: {
    background: '#eff6ff',
    color: '#1e40af',
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
    border: '1px solid #bfdbfe',
  },
  errorBanner: {
    background: '#fee2e2',
    color: '#b91c1c',
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
    border: '1px solid #fecaca',
  },
  modalOverlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0,0,0,0.4)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modalContent: {
    background: '#fff',
    width: 480,
    padding: 24,
    borderRadius: 8,
    boxShadow: '0 10px 25px rgba(0,0,0,0.1)',
  },
  formRow: { display: 'flex', gap: 12, marginBottom: 12 },
  label: { display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4, color: '#334155' },
  input: {
    width: '100%',
    padding: '8px 10px',
    borderRadius: 6,
    border: '1px solid #cbd5e1',
    boxSizing: 'border-box',
    fontSize: 14,
    outline: 'none',
  },
};