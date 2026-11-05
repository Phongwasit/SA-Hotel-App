package th.ac.hotel.model;

import java.util.List;

/** Joined DB rows grouped into bookings/items and rooms with active-stay intervals. */
public record BookingContext(List<Room> rooms, List<Booking> bookings) {
    public BookingContext { rooms = List.copyOf(rooms); bookings = List.copyOf(bookings); }
}
