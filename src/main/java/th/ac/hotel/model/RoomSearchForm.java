package th.ac.hotel.model;

public class RoomSearchForm {
    private String roomType = "Standard";
    private String checkInDate = "";
    private String checkOutDate = "";
    private String requiredRooms = "1";
    public String getRoomType() { return roomType; }
    public void setRoomType(String value) { roomType = value; }
    public String getCheckInDate() { return checkInDate; }
    public void setCheckInDate(String value) { checkInDate = value; }
    public String getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(String value) { checkOutDate = value; }
    public String getRequiredRooms() { return requiredRooms; }
    public void setRequiredRooms(String value) { requiredRooms = value; }
}
