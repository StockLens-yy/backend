import { useEffect, useRef, useState } from 'react';
import Chart from 'chart.js/auto';
import api from '../api/axios';

function ChartModal({ stock, onClose }) {
    const chartRef = useRef(null);
    const chartInstance = useRef(null);
    const [chartType, setChartType] = useState('daily');

    useEffect(() => {
        loadChart(chartType);
        return () => {
            if (chartInstance.current) chartInstance.current.destroy();
        };
    }, [chartType]);

    const loadChart = async (type) => {
        try {
            const res = await api.get(`/api/stocks/chart/${stock.symbol}/${type}`);
            const data = res.data;

            const labels = data.map(d => {
                const date = d.date;
                return `${date.slice(0,4)}.${date.slice(4,6)}.${date.slice(6,8)}`;
            }).reverse();
            const prices = data.map(d => parseFloat(d.close)).reverse();

            if (chartInstance.current) chartInstance.current.destroy();

            const isUp = prices[prices.length - 1] >= prices[0];
            const color = isUp ? '#dc2626' : '#2563eb';

            chartInstance.current = new Chart(chartRef.current, {
                type: 'line',
                data: {
                    labels,
                    datasets: [{
                        data: prices,
                        borderColor: color,
                        backgroundColor: isUp ? 'rgba(220,38,38,0.05)' : 'rgba(37,99,235,0.05)',
                        borderWidth: 1.5,
                        pointRadius: 0,
                        fill: true,
                        tension: 0.1,
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { ticks: { color: '#9ca3af', maxTicksLimit: 8, font: { size: 11 } }, grid: { color: '#f3f4f6' } },
                        y: { ticks: { color: '#9ca3af', callback: v => v.toLocaleString(), font: { size: 11 } }, grid: { color: '#f3f4f6' } }
                    }
                }
            });
        } catch (e) {
            console.error('차트 로드 실패', e);
        }
    };

    const changeColor = stock.change > 0 ? '#dc2626' : stock.change < 0 ? '#2563eb' : '#6b7280';

    return (
        <div style={styles.overlay} onClick={onClose}>
            <div style={styles.modal} onClick={e => e.stopPropagation()}>
                <div style={styles.header}>
                    <div>
                        <div style={styles.title}>{stock.name}</div>
                        <div style={styles.symbol}>{stock.symbol}</div>
                    </div>
                    <button style={styles.closeBtn} onClick={onClose}>✕</button>
                </div>

                <div style={styles.infoBar}>
                    <div style={styles.infoItem}>
                        <label style={styles.infoLabel}>현재가</label>
                        <strong style={styles.infoValue}>{stock.price?.toLocaleString()}원</strong>
                    </div>
                    <div style={styles.infoItem}>
                        <label style={styles.infoLabel}>전일대비</label>
                        <strong style={{...styles.infoValue, color: changeColor}}>
                            {stock.change > 0 ? '+' : ''}{stock.change?.toLocaleString()}
                        </strong>
                    </div>
                    <div style={styles.infoItem}>
                        <label style={styles.infoLabel}>등락률</label>
                        <strong style={{...styles.infoValue, color: changeColor}}>
                            {stock.changePercent > 0 ? '+' : ''}{stock.changePercent}%
                        </strong>
                    </div>
                    <div style={styles.infoItem}>
                        <label style={styles.infoLabel}>거래량</label>
                        <strong style={styles.infoValue}>{stock.volume?.toLocaleString()}</strong>
                    </div>
                </div>

                <div style={styles.chartTabs}>
                    <button
                        style={{...styles.chartTab, ...(chartType === 'daily' ? styles.chartTabActive : {})}}
                        onClick={() => setChartType('daily')}>일봉</button>
                    <button
                        style={{...styles.chartTab, ...(chartType === 'weekly' ? styles.chartTabActive : {})}}
                        onClick={() => setChartType('weekly')}>주봉</button>
                </div>

                <div style={styles.chartContainer}>
                    <canvas ref={chartRef}></canvas>
                </div>
            </div>
        </div>
    );
}

const styles = {
    overlay: { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.4)', zIndex: 1000, display: 'flex', justifyContent: 'center', alignItems: 'center' },
    modal: { background: 'white', borderRadius: '12px', padding: '24px', width: '760px', maxWidth: '95vw', boxShadow: '0 20px 60px rgba(0,0,0,0.15)' },
    header: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' },
    title: { fontSize: '16px', fontWeight: 700, color: '#111827' },
    symbol: { fontSize: '12px', color: '#9ca3af', marginTop: '2px' },
    closeBtn: { background: 'none', border: 'none', color: '#9ca3af', fontSize: '20px', cursor: 'pointer' },
    infoBar: { display: 'flex', gap: '24px', marginBottom: '16px', padding: '12px 16px', background: '#f9fafb', borderRadius: '8px' },
    infoItem: { display: 'flex', flexDirection: 'column' },
    infoLabel: { fontSize: '11px', color: '#9ca3af', marginBottom: '2px' },
    infoValue: { fontSize: '14px', fontWeight: 600, color: '#111827' },
    chartTabs: { display: 'flex', gap: '4px', marginBottom: '12px' },
    chartTab: { background: '#f1f3f5', border: 'none', color: '#6b7280', padding: '5px 12px', borderRadius: '5px', cursor: 'pointer', fontSize: '12px', fontWeight: 500 },
    chartTabActive: { background: '#2563eb', color: 'white' },
    chartContainer: { position: 'relative', height: '280px' },
};

export default ChartModal;