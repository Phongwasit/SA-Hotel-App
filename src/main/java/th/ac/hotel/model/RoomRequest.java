package th.ac.hotel.model;

import java.time.LocalDate;

public record RoomRequest(int roomId, LocalDate checkInDate, LocalDate checkOutDate) {}
