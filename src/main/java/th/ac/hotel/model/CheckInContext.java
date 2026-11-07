package th.ac.hotel.model;
import java.util.*;
public record CheckInContext(List<Room> rooms,Map<Integer,BookingItem> bookingItems,Set<Integer> activeRoomIds) {
    public CheckInContext {rooms=List.copyOf(rooms);bookingItems=Map.copyOf(bookingItems);activeRoomIds=Set.copyOf(activeRoomIds);}
}
