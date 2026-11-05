SELECT r.*, rt.standard_nightly_rate, b.booking_id, b.booker_name, b.phone,
       b.staff_id, b.created_at,
       bi.booking_item_id, bi.booking_id AS item_booking_id,
       bi.room_id AS item_room_id,
       bi.check_in_date AS booked_check_in,
       bi.check_out_date AS booked_check_out,
       bi.status AS item_status,
       s.stay_id, s.room_id AS stay_room_id,
       s.check_in_date AS stay_check_in,
       s.expected_check_out_date AS stay_check_out,
       s.status AS stay_status
FROM room r
JOIN room_type rt ON rt.type_name=r.type_name
LEFT JOIN booking_item bi ON bi.room_id = r.room_id
LEFT JOIN booking b ON b.booking_id = bi.booking_id
LEFT JOIN stay s ON s.room_id = r.room_id AND s.status = 'ACTIVE'
ORDER BY r.room_number, b.booking_id, bi.booking_item_id;
