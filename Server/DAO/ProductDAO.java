package Server.DAO;

import Server.Model.Product;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class ProductDAO extends DAO {

    public ProductDAO() {
        super();
    }

    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        double weight = 1.0;
        double realVal = rs.getDouble("price");
        String category = null;
        try {
            weight = rs.getDouble("weight");
        } catch (SQLException ignored) {}
        try {
            realVal = rs.getDouble("realValue");
        } catch (SQLException ignored) {}
        try {
            category = rs.getString("category");
        } catch (SQLException ignored) {} // DB cũ chưa có cột category

        return new Product(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("amount"),
                rs.getDouble("price"),
                weight,
                realVal,
                rs.getString("picture"),
                category
        );
    }

    // Thêm sản phẩm
    public void addProduct(Product product) {
        try {
            String query = "INSERT INTO product(name, amount, price, weight, realValue, picture, category) VALUES(?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, product.getName());
            stm.setString(2, product.getAmount());
            stm.setDouble(3, product.getPrice());
            stm.setDouble(4, product.getWeight());
            stm.setDouble(5, product.getRealValue());
            stm.setString(6, product.getPicture());
            stm.setString(7, product.getCategory());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Sửa thông tin sản phẩm
    public void updateProduct(Product product) {
        try {
            String query = "UPDATE product SET name = ?, amount = ?, price = ?, weight = ?, realValue = ?, picture = ?, category = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, product.getName());
            stm.setString(2, product.getAmount());
            stm.setDouble(3, product.getPrice());
            stm.setDouble(4, product.getWeight());
            stm.setDouble(5, product.getRealValue());
            stm.setString(6, product.getPicture());
            stm.setString(7, product.getCategory());
            stm.setInt(8, product.getId());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Xoá sản phẩm
    public void deleteProduct(Product product) {
        try {
            String query = "DELETE FROM product WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, product.getId());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy sản phẩm theo ID
    public Product getProductByID(int id) {
        try {
            String query = "SELECT * FROM product WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return mapResultSetToProduct(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy sản phẩm ngẫu nhiên đơn
    public Product getRandomProduct() {
        ArrayList<Product> products = getAllProducts();
        if (products.isEmpty()) return null;
        return products.get(new Random().nextInt(products.size()));
    }

    // Lấy N sản phẩm ngẫu nhiên không trùng nhau cho Thùng Hàng Bí Ẩn
    public ArrayList<Product> getRandomProducts(int count) {
        ArrayList<Product> all = getAllProducts();
        Collections.shuffle(all);
        ArrayList<Product> result = new ArrayList<>();
        int limit = Math.min(count, all.size());
        for (int i = 0; i < limit; i++) {
            result.add(all.get(i));
        }
        return result;
    }

    /**
     * Lấy N sản phẩm ngẫu nhiên KHÔNG trùng, tổng giá trị thực KHÔNG vượt maxTotalValue
     * (đồng bộ khoảng ví của hạng phòng — hạng Bình Dân không gặp thùng giá 50 triệu).
     * Nếu không đủ ràng buộc, nới lỏng dần để vẫn tạo được thùng hàng.
     */
    public ArrayList<Product> getRandomProducts(int count, double maxTotalValue) {
        return getRandomProducts(count, maxTotalValue, null);
    }

    /**
     * Lấy N sản phẩm ngẫu nhiên theo CHỦ ĐỀ (category), tổng giá trị thực ≤ maxTotalValue
     * (trần thùng theo hạng phòng — "giá không vượt mức phòng", đã chốt).
     * category null/rỗng → không lọc chủ đề (hành vi cũ).
     * Nới lỏng: nếu chủ đề thiếu món thấp thì hạ dần ngưỡng, cuối cùng chọn đủ count.
     */
    public ArrayList<Product> getRandomProducts(int count, double maxTotalValue, String category) {
        ArrayList<Product> all = getAllProducts();
        ArrayList<Product> pool = new ArrayList<>();
        for (Product p : all) {
            if (category == null || category.isBlank()
                    || (p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))) {
                pool.add(p);
            }
        }
        if (pool.isEmpty()) return getRandomProducts(count); // chủ đề rỗng → không lọc
        Collections.shuffle(pool);

        ArrayList<Product> result = new ArrayList<>();
        double sum = 0;
        for (Product p : pool) {
            if (result.size() >= count) break;
            if (sum + p.getRealValue() <= maxTotalValue) {
                result.add(p);
                sum += p.getRealValue();
            }
        }
        // Nới lỏng 1: bỏ món đắt nhất, lấy thêm món rẻ (đủ count ưu tiên hơn)
        if (result.size() < Math.min(count, pool.size())) {
            result.clear();
            sum = 0;
            ArrayList<Product> sorted = new ArrayList<>(pool);
            sorted.sort((a, b) -> Double.compare(a.getRealValue(), b.getRealValue()));
            for (Product p : sorted) {
                if (result.size() >= count) break;
                result.add(p);
                sum += p.getRealValue();
            }
        }
        return result;
    }

    // Tìm kiếm sản phẩm
    public ArrayList<Product> searchProducts(String keyword) {
        ArrayList<Product> products = new ArrayList<>();
        try {
            String query = "SELECT * FROM product WHERE name LIKE ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, "%" + keyword + "%");
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    // Lấy danh sách tất cả sản phẩm
    public ArrayList<Product> getAllProducts() {
        ArrayList<Product> products = new ArrayList<>();
        try {
            String query = "SELECT * FROM product";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }
}
