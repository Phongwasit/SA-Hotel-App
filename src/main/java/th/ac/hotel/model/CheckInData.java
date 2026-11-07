package th.ac.hotel.model;
import java.util.*;
public record CheckInData(List<StayAccount> stays,List<Room> rooms,List<Integer> selectedBookingItemIds) {
    public CheckInData {stays=List.copyOf(stays);rooms=List.copyOf(rooms);selectedBookingItemIds=List.copyOf(selectedBookingItemIds);}
}
