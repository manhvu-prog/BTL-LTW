package TestClient.ViewFx;

import javafx.scene.Parent;

/**
 * ContentPanel — thay thế BaseView đa cửa sổ.
 * Mọi "màn hình" client giờ là MỘT Parent được swap vào AppShell (khung 1 cửa sổ
 * duy nhất) — không tạo Stage/popup Windows mới (đã chốt với người dùng).
 *
 * Chu kỳ sống:
 *  1. FxUi tạo panel (constructor con gán field).
 *  2. attach() → dựng content() LƯỜI (sau khi constructor con hoàn tất, tránh
 *     lỗi field null như BaseView cũ) → AppShell.setContent(key, parent, onHide).
 *  3. onHide() được AppShell gọi trước khi panel khác được swap vào — nơi dừng
 *     timer/đồng hồ nền (tương đương stopAllThread cũ).
 */
public abstract class ContentPanel {
    private Parent built;
    private boolean attached = false;

    /** Key điều hướng trong AppShell ("login","lobby","roomlist","findroom","waiting","auction","rankings","online"). */
    protected abstract String key();

    /** Tiêu đề vùng nội dung (AppShell không đổi title cửa sổ — chỉ 1 cửa sổ duy nhất). */
    protected abstract String title();

    /** Dựng nội dung — gọi 1 lần trong attach() sau khi constructor con hoàn tất. */
    protected abstract Parent content() throws Exception;

    /** Dừng timer/tác vụ nền (con override khi cần) — AppShell gọi trước khi swap panel khác. */
    public void onHide() {}

    /** Đưa panel vào khung — FxUi gọi thay cho view.show() cũ. */
    public void attach() {
        if (!attached) {
            try {
                built = content();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            attached = true;
        }
        AppShell.clearBanners();
        AppShell.setContent(key(), built, this::onHide);
    }

    /** Panel còn đang hiển thị trong khung không. */
    public boolean isShowing() {
        return attached && AppShell.currentKey().equals(key());
    }

    /** Gỡ panel khỏi khung mà không hủy nội dung (giữ field để update sau). */
    public void detach() {
        if (onHideThrows()) { /* giữ hành vi an toàn */ }
        attached = false;
    }

    private boolean onHideThrows() { return false; }
}
