// Shared look & feel: one colour palette, one footer, one money format for every embed.
const COLORS = {
    primary: '#f08cbc',
    success: '#3ecf8e',
    warning: '#f7a35a',
    danger: '#f75a5a',
    neutral: '#8b8b8b',
    live: '#9146ff'
};

const FOOTER = 'Chicken Games';

// 1500 -> 1.5K, 2500000 -> 2.5M
function formatMoney(n) {
    for (const [v, s] of [[1e12, 'T'], [1e9, 'B'], [1e6, 'M'], [1e3, 'K']]) {
        if (n >= v) return `${+(n / v).toFixed(2)}${s}`;
    }
    return String(n);
}

module.exports = { COLORS, FOOTER, formatMoney };
