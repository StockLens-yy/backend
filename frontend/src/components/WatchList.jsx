import api from '../api/axios';

function WatchList({ watchlist, setWatchlist, stocks }) {
    const removeWatch = async (symbol) => {
        const params = new URLSearchParams();
        params.append('symbol', symbol);
        await api.post('/api/stocks/watchlist/remove', params);
        setWatchlist(watchlist.filter(w => w.symbol !== symbol));
    };

    const watchedStocks = watchlist.map(w => {
        const stock = stocks.find(s => s.symbol === w.symbol);
        return stock || { symbol: w.symbol, name: w.name, price: null, change: null, changePercent: null, volume: null };
    });

    if (watchlist.length === 0) {
        return (
            <div style={styles.empty}>
                관심종목을 추가해보세요
            </div>
        );
    }

    return (
        <div style={styles.tableWrap}>
            <table style={styles.table}>
                <thead>
                    <tr>
                        <th style={{...styles.th, textAlign: 'left'}}>종목명</th>
                        <th style={styles.th}>현재가</th>
                        <th style={styles.th}>전일대비</th>
                        <th style={styles.th}>등락률</th>
                        <th style={styles.th}>거래량</th>
                        <th style={styles.th}></th>
                    </tr>
                </thead>
                <tbody>
                    {watchedStocks.map(stock => {
                        const changeColor = stock.change > 0 ? '#dc2626' : stock.change < 0 ? '#2563eb' : '#6b7280';
                        return (
                            <tr key={stock.symbol} style={styles.tr}>
                                <td style={{...styles.td, textAlign: 'left'}}>
                                    <div style={styles.stockName}>{stock.name}</div>
                                    <div style={styles.stockCode}>{stock.symbol}</div>
                                </td>
                                <td style={{...styles.td, fontWeight: 600}}>
                                    {stock.price ? `${stock.price.toLocaleString()}원` : '-'}
                                </td>
                                <td style={{...styles.td, color: changeColor}}>
                                    {stock.change != null ? `${stock.change > 0 ? '+' : ''}${stock.change.toLocaleString()}` : '-'}
                                </td>
                                <td style={{...styles.td, color: changeColor}}>
                                    {stock.changePercent != null ? `${stock.changePercent > 0 ? '+' : ''}${stock.changePercent}%` : '-'}
                                </td>
                                <td style={{...styles.td, color: '#6b7280', fontSize: '12px'}}>
                                    {stock.volume ? stock.volume.toLocaleString() : '-'}
                                </td>
                                <td style={styles.td}>
                                    <button
                                        onClick={() => removeWatch(stock.symbol)}
                                        style={styles.removeBtn}>✕</button>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

const styles = {
    tableWrap: { background: 'white', borderRadius: '10px', border: '1px solid #e8eaed', overflow: 'hidden', marginTop: '16px' },
    table: { width: '100%', borderCollapse: 'collapse' },
    th: { padding: '11px 16px', textAlign: 'right', fontSize: '11px', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px', background: '#f9fafb', borderBottom: '1px solid #e8eaed' },
    tr: { borderBottom: '1px solid #f3f4f6' },
    td: { padding: '13px 16px', textAlign: 'right', fontSize: '13px', color: '#374151' },
    stockName: { fontWeight: 600, fontSize: '13px', color: '#111827' },
    stockCode: { fontSize: '11px', color: '#9ca3af', marginTop: '2px' },
    removeBtn: { background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af', fontSize: '14px', padding: '2px' },
    empty: { textAlign: 'center', padding: '60px', color: '#9ca3af', fontSize: '14px', background: 'white', borderRadius: '10px', border: '1px solid #e8eaed', marginTop: '16px' },
};

export default WatchList;