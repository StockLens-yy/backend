import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axios';

function Header({ user, setUser }) {
    const navigate = useNavigate();

    const handleLogout = async () => {
        await api.post('/api/auth/logout');
        setUser(null);
        navigate('/');
    };

    return (
        <header style={styles.header}>
            <Link to="/" style={styles.logo}>
                Stock<span style={{ color: '#6366f1' }}>Lens</span>
            </Link>
            <div style={styles.right}>
                {user ? (
                    <>
                        <div style={styles.userBadge}>
                            <div style={styles.avatar}>{user.name?.[0]?.toUpperCase()}</div>
                            <span style={styles.userName}>{user.name}</span>
                        </div>
                        <button onClick={handleLogout} style={styles.btnOutline}>로그아웃</button>
                    </>
                ) : (
                    <>
                        <Link to="/login" style={styles.btnGhost}>로그인</Link>
                        <Link to="/register" style={styles.btnOutline}>회원가입</Link>
                        <a href="http://localhost:8080/oauth2/authorization/google" style={styles.btnPrimary}>
                            Google로 시작
                        </a>
                    </>
                )}
            </div>
        </header>
    );
}

const styles = {
    header: {
        background: 'rgba(255,255,255,0.8)',
        backdropFilter: 'blur(12px)',
        padding: '0 48px',
        borderBottom: '1px solid #e2e8f0',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        height: '60px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
    },
    logo: {
        fontSize: '20px',
        fontWeight: 700,
        color: '#0f172a',
        textDecoration: 'none',
        letterSpacing: '-0.5px',
    },
    right: { display: 'flex', alignItems: 'center', gap: '8px' },
    userBadge: { display: 'flex', alignItems: 'center', gap: '8px', marginRight: '4px' },
    avatar: {
        width: '32px', height: '32px', borderRadius: '50%',
        background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
        color: 'white', display: 'flex', alignItems: 'center',
        justifyContent: 'center', fontSize: '13px', fontWeight: 600,
    },
    userName: { fontSize: '13px', color: '#475569', fontWeight: 500 },
    btnPrimary: {
        padding: '8px 16px', borderRadius: '8px', fontSize: '13px',
        fontWeight: 600, background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
        color: 'white', textDecoration: 'none', border: 'none', cursor: 'pointer',
        boxShadow: '0 2px 8px rgba(99,102,241,0.3)',
    },
    btnOutline: {
        padding: '8px 16px', borderRadius: '8px', fontSize: '13px',
        fontWeight: 500, background: 'white', color: '#475569',
        textDecoration: 'none', border: '1px solid #e2e8f0', cursor: 'pointer',
    },
    btnGhost: {
        padding: '8px 16px', borderRadius: '8px', fontSize: '13px',
        fontWeight: 500, background: 'transparent', color: '#64748b',
        textDecoration: 'none', border: 'none', cursor: 'pointer',
    },
};

export default Header;