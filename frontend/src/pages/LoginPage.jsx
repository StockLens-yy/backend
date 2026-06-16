import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';

function LoginPage({ setUser }) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            const params = new URLSearchParams();
            params.append('email', email);
            params.append('password', password);

            await api.post('/api/auth/login', params, {
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
            });

            const res = await api.get('/api/auth/me');
            setUser(res.data);
            navigate('/');
        } catch (err) {
            setError('이메일 또는 비밀번호가 올바르지 않습니다.');
        }
    };

    return (
        <div style={styles.container}>
            <div style={styles.card}>
                <div style={styles.logo}>Stock<span style={{ color: '#2563eb' }}>Lens</span></div>
                <p style={styles.subtitle}>계속하려면 로그인하세요</p>

                {error && <div style={styles.error}>{error}</div>}

                <form onSubmit={handleLogin}>
                    <div style={styles.formGroup}>
                        <label style={styles.label}>이메일</label>
                        <input
                            type="email"
                            value={email}
                            onChange={e => setEmail(e.target.value)}
                            placeholder="name@email.com"
                            style={styles.input}
                            required
                        />
                    </div>
                    <div style={styles.formGroup}>
                        <label style={styles.label}>비밀번호</label>
                        <input
                            type="password"
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            placeholder="비밀번호 입력"
                            style={styles.input}
                            required
                        />
                    </div>
                    <button type="submit" style={styles.btnPrimary}>로그인</button>
                </form>

                <div style={styles.divider}><span>또는</span></div>

                <a href="http://localhost:8080/oauth2/authorization/google" style={styles.btnGoogle}>
                    Google 계정으로 로그인
                </a>

                <div style={styles.footer}>
                    계정이 없으신가요? <Link to="/register" style={styles.link}>회원가입</Link>
                </div>
            </div>
        </div>
    );
}

const styles = {
    container: { background: '#f5f6f8', display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 'calc(100vh - 56px)' },
    card: { background: 'white', borderRadius: '12px', padding: '40px', width: '380px', border: '1px solid #e8eaed' },
    logo: { fontSize: '20px', fontWeight: 700, color: '#1a1a2e', textAlign: 'center', marginBottom: '6px' },
    subtitle: { textAlign: 'center', fontSize: '13px', color: '#9ca3af', marginBottom: '28px' },
    formGroup: { marginBottom: '14px' },
    label: { display: 'block', fontSize: '12px', fontWeight: 500, color: '#374151', marginBottom: '6px' },
    input: { width: '100%', padding: '10px 12px', border: '1px solid #e8eaed', borderRadius: '7px', color: '#111827', fontSize: '13px', outline: 'none', boxSizing: 'border-box' },
    btnPrimary: { width: '100%', padding: '11px', background: '#2563eb', color: 'white', border: 'none', borderRadius: '7px', fontSize: '14px', fontWeight: 500, cursor: 'pointer', marginTop: '4px' },
    divider: { display: 'flex', alignItems: 'center', gap: '12px', margin: '16px 0', color: '#9ca3af', fontSize: '12px' },
    btnGoogle: { width: '100%', padding: '11px', background: 'white', color: '#374151', border: '1px solid #e8eaed', borderRadius: '7px', fontSize: '13px', fontWeight: 500, cursor: 'pointer', textDecoration: 'none', display: 'block', textAlign: 'center', boxSizing: 'border-box' },
    error: { background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626', padding: '10px 12px', borderRadius: '7px', fontSize: '12px', marginBottom: '16px' },
    footer: { textAlign: 'center', marginTop: '20px', fontSize: '12px', color: '#9ca3af' },
    link: { color: '#2563eb', textDecoration: 'none', fontWeight: 500 },
};

export default LoginPage;