-- Demo catalogue. RIBBON-CABLE is deliberately scarce so the console can show a rejected order.
INSERT INTO products (sku, name, unit_price, currency, available) VALUES
    ('CHARGER-35W',   'USB-C charger, 35 W dual port',    39.00, 'USD', 500),
    ('BRAID-CABLE-1M','Braided USB-C cable, 1 m',          19.00, 'USD', 1200),
    ('EARBUDS-ANC',   'Wireless earbuds with noise control', 179.00, 'USD', 240),
    ('SLEEVE-14',     'Wool laptop sleeve, 14 inch',       79.00, 'USD', 150),
    ('WATCH-BAND-S',  'Sport watch band, small',           49.00, 'USD', 400),
    ('STYLUS-2',      'Tablet stylus, second generation', 129.00, 'USD', 90),
    ('DISPLAY-27',    '27 inch 5K display',              1599.00, 'USD', 12),
    ('RIBBON-CABLE',  'Limited edition woven cable',       29.00, 'USD', 3);
