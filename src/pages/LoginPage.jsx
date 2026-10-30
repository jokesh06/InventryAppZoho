import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('Admin@12345');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(username, password);
    } catch (err) {
      setError(err.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <form onSubmit={handleSubmit} style={styles.card}>
        <div style={styles.logoBadge}>IOMS</div>
        <h2 style={{ margin: '12px 0 4px', color: '#0f172a' }}>Sign In to Portal</h2>
        <p style={{ margin: '0 0 20px', color: '#64748b', fontSize: 13 }}>        
        </p>

        {error && <div style={styles.errorBox}>{error}</div>}

        <div style={styles.field}>
          <label style={styles.label}>Username</label>
          <input
            type="text"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            style={styles.input}
          />
        </div>

        <div style={styles.field}>
          <label style={styles.label}>Password</label>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={styles.input}
          />
        </div>

        <button type="submit" disabled={loading} style={styles.submitBtn}>
          {loading ? 'Authenticating...' : 'Sign In'}
        </button>
      </form>
    </div>
  );
}

const styles = {
  container: {
    height: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#f8fafc',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  card: {
    width: 360,
    background: '#ffffff',
    padding: 32,
    borderRadius: 10,
    boxShadow: '0 4px 20px rgba(0,0,0,0.06)',
    border: '1px solid #e2e8f0',
  },
  logoBadge: {
    display: 'inline-block',
    background: '#0284c7',
    color: '#fff',
    fontWeight: 800,
    fontSize: 12,
    padding: '3px 8px',
    borderRadius: 4,
    letterSpacing: 1,
  },
  field: { marginBottom: 16 },
  label: { display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 4 },
  input: {
    width: '100%',
    padding: '9px 12px',
    borderRadius: 6,
    border: '1px solid #cbd5e1',
    boxSizing: 'border-box',
    fontSize: 14,
    outline: 'none',
  },
  submitBtn: {
    width: '100%',
    padding: 10,
    backgroundColor: '#0284c7',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    fontWeight: 600,
    cursor: 'pointer',
    marginTop: 8,
  },
  errorBox: {
    backgroundColor: '#fee2e2',
    color: '#991b1b',
    padding: 10,
    borderRadius: 6,
    fontSize: 13,
    marginBottom: 16,
  },
};