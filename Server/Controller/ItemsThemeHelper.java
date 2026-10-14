package Server.Controller;

/**
 * ItemsThemeHelper — bảng chuyển key chủ đề (Product.category) ↔ tên hiển thị.
 * Dùng chung cho server (gửi auction-start, lưu DB, log) và client parse.
 * Đặt ở package Server.Controller vì client đã import Server.Model / class này
 * phải đứng một mình, không phụ thuộc DAO.
 */
public final class ItemsThemeHelper {

    private ItemsThemeHelper() {}

    /** Tên hiển thị tiếng Việt theo key. Key lạ → trả nguyên key (an toàn). */
    public static String displayName(String key) {
        if (key == null) return "Bí ẩn";
        return switch (key) {
            case "CONG_NGHE"      -> "Công nghệ";
            case "TRANG_SUC"      -> "Trang sức";
            case "O_TO"           -> "Ô tô";
            case "THOI_TRANG"     -> "Thời trang";
            case "GIA_DUNG"       -> "Gia dụng";
            case "THE_THAO"       -> "Thể thao";
            case "THUC_PHAM"      -> "Thực phẩm";
            case "SACH_VAN_PHONG" -> "Sách & Văn phòng";
            case "DO_CHOI"        -> "Đồ chơi";
            case "MY_PHAM"        -> "Mỹ phẩm";
            case "NHAC_CU"        -> "Nhạc cụ";
            case "DO_CO"          -> "Đồ cổ & Sưu tầm";
            case "THU_CUNG"       -> "Thú cưng";
            case "DU_LICH"        -> "Du lịch & Dã ngoại";
            default               -> key;
        };
    }

    /** Emoji đại diện từng chủ đề (hiển thị trên UI). */
    public static String emoji(String key) {
        if (key == null) return "📦";
        return switch (key) {
            case "CONG_NGHE"      -> "💻";
            case "TRANG_SUC"      -> "💎";
            case "O_TO"           -> "🚗";
            case "THOI_TRANG"     -> "👕";
            case "GIA_DUNG"       -> "🏠";
            case "THE_THAO"       -> "⚽";
            case "THUC_PHAM"      -> "🍜";
            case "SACH_VAN_PHONG" -> "📚";
            case "DO_CHOI"        -> "🧸";
            case "MY_PHAM"        -> "💄";
            case "NHAC_CU"        -> "🎸";
            case "DO_CO"          -> "🏺";
            case "THU_CUNG"       -> "🐾";
            case "DU_LICH"        -> "🏕️";
            default               -> "📦";
        };
    }
}
