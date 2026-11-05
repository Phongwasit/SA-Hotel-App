package th.ac.hotel.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import th.ac.hotel.model.BlockingInterval;
import th.ac.hotel.model.Room;
import th.ac.hotel.model.RoomCollection;
import th.ac.hotel.model.RoomSearchForm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RoomListController {
    private static final Logger log = LoggerFactory.getLogger(RoomListController.class);
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final List<String> ROOM_TYPES = List.of("Standard", "Deluxe", "Suite");
    private final JdbcTemplate jdbcTemplate;
    private final String queryUC01_1;

    public RoomListController(JdbcTemplate jdbcTemplate) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.queryUC01_1 = new ClassPathResource("sql/queryUC01_1.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping("/rooms")
    public String roomList(Model model, HttpServletResponse response) {
        // 1. query via JdbcTemplate; 2. storeRoomList; 3. display inputs.
        loadRoomList(model, response);
        var form = new RoomSearchForm();
        LocalDate today = LocalDate.now(BANGKOK);
        form.setCheckInDate(today.toString());
        form.setCheckOutDate(today.plusDays(1).toString());
        model.addAttribute("form", form);
        return "rooms";
    }

    @PostMapping("/rooms/search")
    public String search(@ModelAttribute("form") RoomSearchForm form, Model model, HttpServletResponse response) {
        // Repeat the FULL load/store cycle on every search, including repeated criteria.
        RoomCollection collection = loadRoomList(model, response);
        if (collection == null) return "rooms";

        Map<String, String> errors = new LinkedHashMap<>();
        if (!ROOM_TYPES.contains(form.getRoomType())) errors.put("roomType", "กรุณาเลือกประเภทห้อง");
        LocalDate checkIn = parseDate(form.getCheckInDate(), "checkInDate", errors);
        LocalDate checkOut = parseDate(form.getCheckOutDate(), "checkOutDate", errors);
        if (checkIn != null && checkOut != null && !checkOut.isAfter(checkIn)) {
            errors.put("checkOutDate", "วันออกต้องอยู่หลังวันเริ่มพักอย่างน้อย 1 คืน");
        }
        int requiredRooms = 0;
        try {
            requiredRooms = Integer.parseInt(form.getRequiredRooms());
            if (requiredRooms < 1) throw new NumberFormatException();
        } catch (NumberFormatException | NullPointerException e) {
            errors.put("requiredRooms", "ระบุจำนวนห้องเป็นจำนวนเต็มตั้งแต่ 1 ห้องขึ้นไป");
        }
        model.addAttribute("errors", errors);
        if (!errors.isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "rooms";
        }

        List<Room> matchingRooms = collection.getRooms(form.getRoomType(), checkIn, checkOut, requiredRooms);
        model.addAttribute("searched", true);
        model.addAttribute("matchingRooms", matchingRooms);
        model.addAttribute("availableCount", matchingRooms.size());
        model.addAttribute("requiredCount", requiredRooms);
        model.addAttribute("shortfall", Math.max(0, requiredRooms - matchingRooms.size()));
        model.addAttribute("nights", java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut));
        model.addAttribute("dateSummary", checkIn.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " – " + checkOut.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        return "rooms";
    }

    private RoomCollection loadRoomList(Model model, HttpServletResponse response) {
        model.addAttribute("activePage", "rooms");
        model.addAttribute("roomTypes", ROOM_TYPES);
        model.addAttribute("searched", false);
        model.addAttribute("errors", Map.of());
        model.addAttribute("matchingRooms", List.of());
        try {
            // roomRowMapper is a grouping ResultSetExtractor: one Room per room_id.
            List<Room> availableRooms = jdbcTemplate.query(queryUC01_1, roomRowMapper);
            RoomCollection collection = new RoomCollection();
            collection.storeRoomList(availableRooms);
            model.addAttribute("loadSucceeded", true);
            model.addAttribute("inventoryCount", collection.size());
            return collection;
        } catch (DataAccessException e) {
            log.warn("UC01 room load failed: {}", e.getClass().getSimpleName());
            model.addAttribute("loadSucceeded", false);
            model.addAttribute("loadError", "โหลดข้อมูลห้องไม่สำเร็จ กรุณาตรวจการเชื่อมต่อฐานข้อมูลแล้วลองอีกครั้ง");
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return null; // Never filter an old collection or present a failed load as zero rooms.
        }
    }

    private LocalDate parseDate(String value, String field, Map<String, String> errors) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException | NullPointerException e) {
            errors.put(field, "กรุณาระบุวันที่ให้ถูกต้อง");
            return null;
        }
    }

    private final ResultSetExtractor<List<Room>> roomRowMapper = rs -> {
        Map<Integer, Room> rooms = new LinkedHashMap<>();
        Map<Integer, List<BlockingInterval>> intervals = new LinkedHashMap<>();
        while (rs.next()) {
            int id = rs.getInt("room_id");
            if (!rooms.containsKey(id)) {
                rooms.put(id, readRoom(rs));
                intervals.put(id, new ArrayList<>());
            }
            if (rs.getObject("block_id") != null) {
                intervals.get(id).add(new BlockingInterval(rs.getString("block_kind"), rs.getInt("block_id"),
                        rs.getDate("block_check_in").toLocalDate(), rs.getDate("block_check_out").toLocalDate()));
            }
        }
        List<Room> result = new ArrayList<>();
        rooms.forEach((id, room) -> result.add(new Room(room.roomId(), room.roomNumber(), room.roomType(), room.bedType(),
                room.occupancy(), room.cleanliness(), room.usability(), room.conditionReason(), room.standardNightlyRate(), intervals.get(id))));
        return result;
    };

    private Room readRoom(ResultSet rs) throws SQLException {
        return new Room(rs.getInt("room_id"), rs.getString("room_number"), rs.getString("type_name"),
                rs.getString("bed_type"), rs.getString("occupancy"), rs.getString("cleanliness"), rs.getString("usability"),
                rs.getString("condition_reason"), rs.getBigDecimal("standard_nightly_rate"), List.of());
    }
}
