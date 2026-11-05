"""One-time design-to-migration generator. Applied migrations must never be rewritten."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
source = root.parent.parent / 'Project/_support_from_Project_new_ver_20261101/approved_database_alignment_verification_20261104'
model = json.loads((source / 'model.json').read_text(encoding='utf-8'))
queries = json.loads((source / 'queries.json').read_text(encoding='utf-8'))
schema, dictionary = model['schema'], model['dictionary']
directory = root / 'src/main/resources/db/migration'
directory.mkdir(parents=True, exist_ok=True)
target = directory / 'V1__hotel_business_schema.sql'
if target.exists():
    raise SystemExit('V1 already exists. Use a NEW migration for future schema changes.')

checks = {
    'employee': ["CHAR_LENGTH(TRIM(username)) > 0"],
    'room_type': ["standard_nightly_rate > 0"],
    'room': ["bed_type IN ('DOUBLE','TWIN')", "occupancy IN ('VACANT','OCCUPIED')", "cleanliness IN ('CLEAN','DIRTY')", "usability IN ('USABLE','OUT_OF_ORDER')"],
    'booking_item': ["check_in_date < check_out_date", "status IN ('BOOKED','CANCELLED','CHECKED_IN')"],
    'stay': ["check_in_date < expected_check_out_date", "key_count BETWEEN 1 AND 2", "status IN ('ACTIVE','CHECKED_OUT')", "actual_check_out IS NULL OR actual_check_out >= actual_check_in", "(status='ACTIVE' AND actual_check_out IS NULL) OR (status='CHECKED_OUT' AND actual_check_out IS NOT NULL)"],
    'stay_charge': ["amount > 0"], 'payment_method': ["amount > 0", "method IN ('CASH','TRANSFER','CARD')"],
    'payment_allocation': ["amount > 0"],
    'document_recipient': ["recipient_type IN ('PERSON','ORGANIZATION')", "CHAR_LENGTH(TRIM(recipient_name)) > 0"],
}

remaining = list(schema)
ordered = []
while remaining:
    ready = [table for table in remaining if all(fk[0] in ordered for fk in schema[table].get('fk', {}).values())]
    if not ready:
        raise SystemExit('Unexpected cyclic schema dependency.')
    for table in ready:
        ordered.append(table)
        remaining.remove(table)

sql = ["-- Approved 2026-11-04 model: 16 business tables / 83 stored fields.",
       "-- Flyway's own schema-history table is technical migration metadata.",
       "-- Cross-row full-night/method sums require Controller transaction validation."]
for table in ordered:
    meta = schema[table]
    definitions = []
    for field in dictionary[table]['fields']:
        name = field['field']
        declaration = f"  {name} {field['type']}" + (' NOT NULL' if field['not_null'] else ' NULL')
        if meta['pk'] == [name] and field['type'] == 'INTEGER':
            declaration += ' AUTO_INCREMENT'
        definitions.append(declaration)
    definitions.append('  PRIMARY KEY (' + ','.join(meta['pk']) + ')')
    for number, columns in enumerate(meta.get('unique', []), 1):
        definitions.append(f"  CONSTRAINT uq_{table}_{number} UNIQUE ({','.join(columns)})")
    for field, (parent, key, nullable) in meta.get('fk', {}).items():
        if table == 'payment_allocation' and field in ['charge_id', 'payment_method_id']:
            continue
        definitions.append(f'  CONSTRAINT fk_{table}_{field} FOREIGN KEY ({field}) REFERENCES {parent}({key})')
    for number, fk in enumerate(model['composite_fks'], 1):
        if fk['table'] == table:
            definitions.append(f"  CONSTRAINT fk_{table}_paired_{number} FOREIGN KEY ({','.join(fk['fields'])}) REFERENCES {fk['parent']}({','.join(fk['parent_fields'])})")
    for number, condition in enumerate(checks.get(table, []), 1):
        definitions.append(f'  CONSTRAINT ck_{table}_{number} CHECK ({condition})')
    sql.append('CREATE TABLE ' + table + ' (\n' + ',\n'.join(definitions) + '\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;')
sql += ["CREATE INDEX ix_booking_item_availability ON booking_item(room_id,status,check_in_date,check_out_date);",
        "CREATE INDEX ix_stay_availability ON stay(room_id,status,check_in_date,expected_check_out_date);"]
target.write_text('\n\n'.join(sql) + '\n', encoding='utf-8')

seed = ["-- Confirmed hotel inventory. No synthetic booking, stay, payment or document records.",
        "INSERT INTO room_type(type_name,standard_nightly_rate,amenities) VALUES ('Standard',800,NULL),('Deluxe',1000,NULL),('Suite',1100,NULL);",
        "INSERT INTO room(room_id,room_number,type_name,bed_type,occupancy,cleanliness,usability,condition_reason) VALUES"]
values = []
for room in dictionary['room']['samples']:
    values.append(f"({room['room_id']},'{room['room_number']}','{room['type_name']}','{room['bed_type']}','VACANT','CLEAN','USABLE',NULL)")
seed.append(',\n'.join(values) + ';')
(directory / 'V2__confirmed_room_inventory.sql').write_text('\n'.join(seed) + '\n', encoding='utf-8')
querydir = root / 'src/main/resources/sql'
querydir.mkdir(exist_ok=True)
(querydir / 'queryUC01_1.sql').write_text(queries['queryUC01_1'].split(';', 1)[0] + ';\n', encoding='utf-8')
print('Created 16-table/83-field MySQL migration, 41 confirmed rooms and canonical UC01 query.')
