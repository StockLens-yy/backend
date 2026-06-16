import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';

function RegisterPage() {
    const [form, setForm] = useState({ name: '', email: '', password: '' });
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleRegister = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/auth/register', form);
            navigate('/login');
        } catch (err) {
            setError(err.response?.data || '회원가입에 실패했습니다.');
        }
    };

    return (
        <div style={styles.container}>
            <div style={styles.card}>
                <div style={styles.logo}>
                    Stock<span style={{ color: '#2563eb' }}>Lens</span>
                </div>
                <p style={styles.subtitle}>계정을 만들고 시작하세요</p>

                {error && <div style={styles.error}>{error}</div>}

                <form onSubmit={handleRegister}>
                    <div style={styles.formGroup}>
                        <label style={styles.label}>이름</label>
                        <input
                            type="text"
                            name="name"
                            value={form.name}
                            onChange={handleChange}
                            placeholder="홍길동"
                            style={styles.input}
                            required
                        />
                    </div>
                    <div style={styles.formGroup}>
                        <label style={styles.label}>이메일</label>
                        <input
                            type="email"
                            name="email"
                            value={form.email}
                            onChange={handleChange}
                            placeholder="name@email.com"
                            style={styles.input}
                            required
                        />
                    </div>
                    <div style={styles.formGroup}>
                        <label style={styles.label}>비밀번호</label>
                        <input
                            type="password"
                            name="password"
                            value={form.password}
                            onChange={handleChange}
                            placeholder="8자 이상 입력"
                            minLength={8}
                            style={styles.input}
                            required
                        />
                    </div>
                    <button type="submit" style={styles.btnPrimary}>
                        회원가입
                    </button>
                </form>

                <div style={styles.footer}>
                    이미 계정이 있으신가요?{' '}
                    <Link to="/login" style={styles.link}>로그인</Link>
                </div>
            </div>
        </div>
    );
}

const styles = {
    container: {
        background: '#f5f6f8',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: 'calc(100vh - 56px)'
    },
    card: {
        background: 'white',
        borderRadius: '12px',
        padding: '40px',
        width: '380px',
        border: '1px solid #e8eaed'
    },
    logo: {
        fontSize: '20px',
        fontWeight: 700,
        color: '#1a1a2e',
        textAlign: 'center',
        marginBottom: '6px'
    },
    subtitle: {
        textAlign: 'center',
        fontSize: '13px',
        color: '#9ca3af',
        marginBottom: '28px'
    },
    formGroup: {
        marginBottom: '14px'
    },
    label: {
        display: 'block',
        fontSize: '12px',
        fontWeight: 500,
        color: '#374151',
        marginBottom: '6px'
    },
    input: {
        width: '100%',
        padding: '10px 12px',
        border: '1px solid #e8eaed',
        borderRadius: '7px',
        color: '#111827',
        fontSize: '13px',
        outline: 'none',
        boxSizing: 'border-box'
    },
    btnPrimary: {
        width: '100%',
        padding: '11px',
        background: '#2563eb',
        color: 'white',
        border: 'none',
        borderRadius: '7px',
        fontSize: '14px',
        fontWeight: 500,
        cursor: 'pointer',
        marginTop: '4px'
    },
    error: {
        background: '#fef2f2',
        border: '1px solid #fecaca',
        color: '#dc2626',
        padding: '10px 12px',
        borderRadius: '7px',
        fontSize: '12px',
        marginBottom: '16px'
    },
    footer: {
        textAlign: 'center',
        marginTop: '20px',
        fontSize: '12px',
        color: '#9ca3af'
    },
    link: {
        color: '#2563eb',
        textDecoration: 'none',
        fontWeight: 500
    },
};

export default RegisterPage;