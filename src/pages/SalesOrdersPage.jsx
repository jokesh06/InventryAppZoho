import React, { useState, useEffect } from 'react';
import { salesOrderApi } from '../api/salesOrderApi.js';
import { productApi } from '../api/productApi.js';
import { warehouseApi } from '../api/warehouseApi.js';

export default function SalesOrdersPage() {
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [showCreate, setShowCreate] = useState(false);
  const [errorToast, setErrorToast] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const [loading, setLoading] = useState(false);

  // Dedicated Shipping Modal State (replaces prompt)
  const [shippingTargetId, setShippingTargetId] = useState(null);
  const [shippingData, setShippingData] = useState({
    carrier: 'FedEx Ground',
    trackingNumber: '',
  });

  const [form, setForm] = useState({
    customerName: 'Alice Johnson',
    customerEmail: 'alice.johnson@example.com',
    warehouseId: '',
    productId: '',
    quantity: 1,
    unitPrice: '',
  });

  useEffect(() => {
    loadLookups();
    loadOrders();
  }, []);

  const loadOrders = async () => {
    setLoading(true);
    try {
      const res = await salesOrderApi.getAll(0, 50);
      console.log('Fetched Sales Orders:', res);

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

      setOrders(list);
    } catch (err) {
      console.error('Failed to load sales orders:', err);
      setErrorToast('Could not fetch orders from MySQL: ' + (err.message || 'Check endpoint'));
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

      const loadedProducts = Array.isArray(prodData)
        ? prodData
        : prodData?.content || prodData?.data || [];
      const loadedWarehouses = Array.isArray(whData)
        ? whData
        : whData?.content || whData?.data || [];

      setProducts(loadedProducts);
      setWarehouses(loadedWarehouses);

      if (loadedWarehouses.length > 0) {
        setForm((prev) => ({
          ...prev,
          warehouseId: loadedWarehouses[0].id,
        }));
      }
    } catch (err) {
      console.error('Failed to load lookups:', err);
    }
  };

  const handleProductSelect = (e) => {
    const selectedId = e.target.value;
    if (!selectedId) {
      setForm((prev) => ({ ...prev, productId: '', unitPrice: '' }));
      return;
    }

    const matchedProduct = products.find((p) => p.id === Number(selectedId));
    setForm((prev) => ({
      ...prev,
      productId: selectedId,
      unitPrice: matchedProduct ? matchedProduct.unitSellingPrice : '',
    }));
  };

  const handleCreateOrder = async (e) => {
    e.preventDefault();
    setErrorToast('');
    setStatusMessage('');

    if (!form.customerName.trim() || !form.customerEmail.trim()) {
      alert('Customer Name and Customer Email are required.');
      return;
    }
    if (!form.warehouseId) {
      alert('Please select a fulfilling warehouse.');
      return;
    }
    if (!form.productId) {
      alert('Please select a product SKU.');
      return;
    }
    if (!form.quantity || Number(form.quantity) < 1) {
      alert('Quantity must be at least 1.');
      return;
    }
    if (form.unitPrice === '' || Number(form.unitPrice) <= 0) {
      alert('Please enter a valid unit price.');
      return;
    }

    const idempotencyKey = 'ORD-TX-' + Math.random().toString(36).substring(2, 9).toUpperCase();

    const payload = {
      customerName: form.customerName.trim(),
      customerEmail: form.customerEmail.trim(),
      warehouseId: Number(form.warehouseId),
      items: [
        {
          productId: Number(form.productId),
          quantity: Number(form.quantity),
          unitPrice: parseFloat(Number(form.unitPrice).toFixed(2)),
        },
      ],
    };

    setLoading(true);
    try {
      const createdOrder = await salesOrderApi.create(payload, idempotencyKey);
      setShowCreate(false);
      setStatusMessage('Order created successfully!');

      if (createdOrder && (createdOrder.id || createdOrder.orderNumber)) {
        setOrders((prev) => [createdOrder, ...prev]);
      }

      setForm((prev) => ({
        ...prev,
        productId: '',
        quantity: 1,
        unitPrice: '',
      }));

      await loadOrders();
    } catch (err) {
      console.error('Create Order Error:', err);
      setErrorToast(err.message || 'Validation failed when creating the sales order.');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (orderId) => {
    setErrorToast('');
    setStatusMessage('');
    try {
      await salesOrderApi.confirm(orderId);
      setStatusMessage(`Order #${orderId} confirmed! Stock reserved with pessimistic lock.`);
      await loadOrders();
    } catch (err) {
      setErrorToast(err.message || '409 Conflict: Insufficient stock available.');
    }
  };

  const openShipModal = (orderId) => {
    setShippingTargetId(orderId);
    setShippingData({
      carrier: 'FedEx Ground',
      trackingNumber: 'FX-' + Math.floor(10000000 + Math.random() * 90000000),
    });
  };

  const executeShip = async (e) => {
    e.preventDefault();
    setErrorToast('');
    setStatusMessage('');

    if (!shippingData.carrier.trim() || !shippingData.trackingNumber.trim()) {
      alert('Carrier and tracking number are required.');
      return;
    }

    try {
      await salesOrderApi.ship(shippingTargetId, shippingData);
      setStatusMessage(`Order #${shippingTargetId} shipped via ${shippingData.carrier}!`);
      setShippingTargetId(null);
      await loadOrders();
    } catch (err) {
      alert(err.message || 'Failed to dispatch shipment.');
    }
  };

  // Helper normalizer for status
  const normalizeStatus = (status) => {
    if (!status) return 'CREATED';
    const s = String(status).toUpperCase().trim();
    if (s.includes('CREATE') || s.includes('DRAFT') || s.includes('PENDING')) return 'CREATED';
    if (s.includes('CONFIRM') || s.includes('RESERVE') || s.includes('HOLD') || s.includes('APPROV')) return 'CONFIRMED';
    if (s.includes('SHIP') || s.includes('DISPATCH') || s.includes('DELIVER')) return 'SHIPPED';
    return s;
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, color: '#0f172a' }}>Sales Orders & Fulfillment</h2>
          <p style={{ margin: '4px 0 0 0', color: '#64748b' }}>
            Lifecycle: <b>CREATED</b> (Draft) → <b>CONFIRMED</b> (Stock Reserved) → <b>SHIPPED</b> (Tracking Generated)
          </p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button onClick={loadOrders} style={styles.secondaryBtn}>
            Refresh
          </button>
          <button onClick={() => setShowCreate(true)} style={styles.primaryBtn}>
            + Create Order
          </button>
        </div>
      </div>

      {statusMessage && (
        <div style={styles.infoBanner}>
          <b>Notice:</b> {statusMessage}
        </div>
      )}

      {errorToast && (
        <div style={styles.conflictBanner}>
          <b>Guard Alert:</b> {errorToast}
        </div>
      )}

      <div style={styles.card}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Order #</th>
              <th style={styles.th}>Customer</th>
              <th style={styles.th}>Total Amount</th>
              <th style={styles.th}>Status</th>
              <th style={styles.th}>Carrier & Tracking</th>
              <th style={{ ...styles.th, textAlign: 'center' }}>Action Buttons</th>
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 ? (
              <tr>
                <td colSpan="6" style={{ textAlign: 'center', padding: 28, color: '#94a3b8' }}>
                  {loading
                    ? 'Fetching orders from database...'
                    : 'No orders found in database. Click "+ Create Order" to place one.'}
                </td>
              </tr>
            ) : (
              orders.map((o) => {
                const normStatus = normalizeStatus(o.status);
                const hasTracking = Boolean(o.trackingNumber);

                return (
                  <tr key={o.id} style={styles.tr}>
                    <td style={styles.td}>
                      <b>{o.orderNumber || `SO-${o.id}`}</b>
                    </td>
                    <td style={styles.td}>
                      {o.customerName
                        ? `${o.customerName} (${o.customerEmail || ''})`
                        : `Customer #${o.customerId || '-'}`}
                    </td>
                    <td style={{ ...styles.td, fontWeight: 700, color: '#0f172a' }}>
                      ${Number(o.totalAmount || 0).toFixed(2)}
                    </td>
                    <td style={styles.td}>
                      <span style={{ ...styles.statusBadge, ...getStatusStyle(normStatus) }}>
                        {o.status || normStatus}
                      </span>
                    </td>
                    <td style={{ ...styles.td, fontSize: 13, color: '#475569' }}>
                      {hasTracking ? (
                        <span style={styles.trackingTag}>
                          🚚 {o.carrier || 'Carrier'}: <b>{o.trackingNumber}</b>
                        </span>
                      ) : (
                        <span style={{ color: '#94a3b8' }}>Not Dispatched</span>
                      )}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'center' }}>
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
                        {/* 1. CREATED STATE */}
                        {normStatus === 'CREATED' && (
                          <button onClick={() => handleConfirm(o.id)} style={styles.actionBtnConfirm}>
                            Confirm & Hold Stock
                          </button>
                        )}

                        {/* 2. CONFIRMED STATE */}
                        {normStatus === 'CONFIRMED' && (
                          <button onClick={() => openShipModal(o.id)} style={styles.actionBtnShip}>
                            Ship & Add Tracking
                          </button>
                        )}

                        {/* 3. SHIPPED STATE */}
                        {normStatus === 'SHIPPED' && (
                          <span style={{ fontSize: 12, color: '#16a34a', fontWeight: 600 }}>
                            ✓ Fulfilled
                          </span>
                        )}

                        {/* Fallback button if status was unexpected */}
                        {normStatus !== 'CREATED' && normStatus !== 'CONFIRMED' && normStatus !== 'SHIPPED' && (
                          <button onClick={() => handleConfirm(o.id)} style={styles.secondaryBtnSmall}>
                            Force Confirm
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* MODAL 1: Create Order Modal */}
      {showCreate && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0, color: '#0f172a' }}>Draft New Sales Order</h3>
            <form onSubmit={handleCreateOrder}>
              <div style={styles.formRow}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Customer Name *</label>
                  <input
                    type="text"
                    required
                    style={styles.input}
                    value={form.customerName}
                    onChange={(e) => setForm({ ...form, customerName: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Customer Email *</label>
                  <input
                    type="email"
                    required
                    style={styles.input}
                    value={form.customerEmail}
                    onChange={(e) => setForm({ ...form, customerEmail: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Fulfilling Warehouse *</label>
                <select
                  required
                  style={styles.input}
                  value={form.warehouseId}
                  onChange={(e) => setForm({ ...form, warehouseId: e.target.value })}
                >
                  <option value="">Select a warehouse...</option>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.code} - {w.name}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={styles.label}>Product SKU *</label>
                <select
                  required
                  style={styles.input}
                  value={form.productId}
                  onChange={handleProductSelect}
                >
                  <option value="">Select SKU...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} - {p.name} (${Number(p.unitSellingPrice || 0).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Quantity *</label>
                  <input
                    type="number"
                    min="1"
                    required
                    style={styles.input}
                    value={form.quantity}
                    onChange={(e) => setForm({ ...form, quantity: e.target.value })}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={styles.label}>Unit Price ($) *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    style={styles.input}
                    value={form.unitPrice}
                    onChange={(e) => setForm({ ...form, unitPrice: e.target.value })}
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
                  {loading ? 'Submitting...' : 'Create Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: In-App Shipping & Tracking Modal */}
      {shippingTargetId && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h3 style={{ marginTop: 0, color: '#0f172a' }}>Dispatch Shipment & Add Tracking</h3>
            <p style={{ fontSize: 13, color: '#64748b', marginTop: -6 }}>
              Fulfilling Sales Order ID: <b>#{shippingTargetId}</b>
            </p>
            <form onSubmit={executeShip}>
              <div style={{ marginBottom: 14 }}>
                <label style={styles.label}>Logistics Carrier *</label>
                <select
                  required
                  style={styles.input}
                  value={shippingData.carrier}
                  onChange={(e) => setShippingData({ ...shippingData, carrier: e.target.value })}
                >
                  <option value="FedEx Ground">FedEx Ground</option>
                  <option value="UPS Next Day Air">UPS Next Day Air</option>
                  <option value="DHL Express">DHL Express</option>
                  <option value="USPS Priority Mail">USPS Priority Mail</option>
                  <option value="BlueDart Logistics">BlueDart Logistics</option>
                </select>
              </div>

              <div style={{ marginBottom: 18 }}>
                <label style={styles.label}>Waybill / Tracking Number *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. TRK-88371902"
                  style={styles.input}
                  value={shippingData.trackingNumber}
                  onChange={(e) => setShippingData({ ...shippingData, trackingNumber: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button
                  type="button"
                  onClick={() => setShippingTargetId(null)}
                  style={styles.cancelBtn}
                >
                  Cancel
                </button>
                <button type="submit" style={styles.actionBtnShip}>
                  Confirm Dispatch & Save Tracking
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

const getStatusStyle = (status) => {
  switch (status) {
    case 'CREATED':
      return { background: '#fef3c7', color: '#b45309' };
    case 'CONFIRMED':
      return { background: '#e0f2fe', color: '#0369a1' };
    case 'SHIPPED':
      return { background: '#dcfce7', color: '#15803d' };
    case 'CANCELLED':
      return { background: '#fee2e2', color: '#b91c1c' };
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
  secondaryBtnSmall: {
    backgroundColor: '#f1f5f9',
    color: '#334155',
    border: '1px solid #cbd5e1',
    padding: '6px 10px',
    borderRadius: 4,
    fontSize: 12,
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
  actionBtnConfirm: {
    background: '#0284c7',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 12,
    fontWeight: 600,
  },
  actionBtnShip: {
    background: '#16a34a',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 12,
    fontWeight: 600,
  },
  trackingTag: {
    display: 'inline-block',
    background: '#f8fafc',
    border: '1px solid #e2e8f0',
    padding: '3px 8px',
    borderRadius: 4,
    fontSize: 12,
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
  statusBadge: { padding: '3px 8px', borderRadius: 4, fontSize: 12, fontWeight: 700 },
  infoBanner: {
    background: '#eff6ff',
    color: '#1e40af',
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
    border: '1px solid #bfdbfe',
  },
  conflictBanner: {
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