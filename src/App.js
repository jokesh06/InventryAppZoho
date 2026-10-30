import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext.jsx';
import LoginPage from './pages/LoginPage.jsx';
import InventoryPage from './pages/InventoryPage.jsx';
import WarehousesPage from './pages/WarehousesPage.jsx';
import SalesOrdersPage from './pages/SalesOrdersPage.jsx';
import ProductsPage from './pages/ProductsPage.jsx';
import PurchaseOrdersPage from './pages/PurchaseOrdersPage.jsx';
import WarehouseStockCheckPage from './pages/WarehouseStockCheckPage.jsx';

function Dashboard() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState('inventory');

  const navItems = [
    { id: 'inventory', label: 'Inventory & Ledger' },
    { id: 'stock-check', label: 'Check Stock by SKU' },
    { id: 'sales-orders', label: 'Sales Orders (Fulfillment)' },
    { id: 'purchase-orders', label: 'Purchase Orders (Restock)' },
    { id: 'products', label: 'Product Catalog' },
    { id: 'warehouses', label: 'Warehouses' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: 'system-ui, sans-serif' }}>
      {/* Top Header */}
      <header style={{ height: 56, background: '#0f172a', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ background: '#0284c7', padding: '3px 8px', borderRadius: 4, fontWeight: 800, fontSize: 12 }}>IOMS</span>
          <span style={{ fontWeight: 700, fontSize: 15 }}>Enterprise Inventory & Order Hub</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span style={{ fontSize: 12, background: '#1e293b', padding: '4px 8px', borderRadius: 12, color: '#38bdf8' }}>
            {user?.role}
          </span>
          <span style={{ fontSize: 13 }}>{user?.fullName || user?.username}</span>
          <button onClick={logout} style={{ background: '#dc2626', color: '#fff', border: 'none', padding: '4px 10px', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
            Logout
          </button>
        </div>
      </header>

      {/* Main Workspace */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Sidebar */}
        <aside style={{ width: 230, background: '#1e293b', padding: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => setTab(item.id)}
              style={{
                textAlign: 'left',
                padding: '10px 12px',
                borderRadius: 6,
                border: 'none',
                background: tab === item.id ? '#0284c7' : 'transparent',
                color: tab === item.id ? '#fff' : '#94a3b8',
                fontWeight: tab === item.id ? 600 : 400,
                cursor: 'pointer',
                fontSize: 14,
              }}
            >
              {item.label}
            </button>
          ))}
        </aside>

        {/* Dynamic Pages */}
        <main style={{ flex: 1, overflowY: 'auto', background: '#f8fafc' }}>
          {tab === 'inventory' && <InventoryPage />}
          {tab === 'stock-check' && <WarehouseStockCheckPage />}
          {tab === 'sales-orders' && <SalesOrdersPage />}
          {tab === 'purchase-orders' && <PurchaseOrdersPage />}
          {tab === 'products' && <ProductsPage />}
          {tab === 'warehouses' && <WarehousesPage />}
        </main>
      </div>
    </div>
  );
}

function Main() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Dashboard /> : <LoginPage />;
}

export default function App() {
  return (
    <AuthProvider>
      <Main />
    </AuthProvider>
  );
}