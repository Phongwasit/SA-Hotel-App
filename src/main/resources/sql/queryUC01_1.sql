SELECT r.*, rt.standard_nightly_rate, b.block_kind, b.block_id,
       b.block_check_in, b.block_check_out
FROM room r
JOIN room_type rt ON rt.type_name=r.type_name
LEFT JOIN (
  SELECT room_id, 'BOOKING' AS block_kind,
         booking_item_id AS block_id,
         check_in_date AS block_check_in,
         check_out_date AS block_check_out
  FROM booking_item
  WHERE status = 'BOOKED'
  UNION ALL
  SELECT room_id, 'STAY' AS block_kind,
         stay_id AS block_id,
         check_in_date AS block_check_in,
         expected_check_out_date AS block_check_out
  FROM stay
  WHERE status = 'ACTIVE'
) b ON b.room_id = r.room_id
ORDER BY r.room_number, b.block_check_in,
         b.block_kind, b.block_id;
