package th.ac.hotel.model;

import java.time.LocalDate;

public record RoomConflict(int roomId, LocalDate checkInDate, LocalDate checkOutDate, String reason) {}
